package io.squark.yggdrasil.jsx.handler;

import io.squark.yggdrasil.jsx.annotation.JsxServletConfig;
import io.squark.yggdrasil.jsx.exception.JsxHandlerException;
import io.squark.yggdrasil.jsx.exception.JsxScriptException;
import io.squark.yggdrasil.jsx.servlet.Response;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.runtime.ScriptObject;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;
import javax.servlet.ServletConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-12. Copyright 2016
 */
@ApplicationScoped
public class JsxHandler {

  public static final String DEBUG_JS_PATH = System.getProperty("timewise.debugJsPath");
  private static final Object scriptEngineLock = new Object();
  private static final Logger logger = LoggerFactory.getLogger(JsxHandler.class);
  private static volatile ScriptObjectMirror babel = null;
  private static volatile ScriptObjectMirror webpackWrapper = null;
  private static volatile NashornScriptEngine scriptEngine = null;
  private static volatile JSObject objectConstructor;
  private static volatile JSObject arrayConstructor;
  private static volatile JSObject babelConfig;
  private static boolean initializeSucceeded = false;

  private ServletConfig servletConfig;

  @Inject
  @JsxServletConfig
  public void setServletConfig(@JsxServletConfig ServletConfig servletConfig) {
    this.servletConfig = servletConfig;
  }

  public void initialize(@Observes ServletConfig servletConfig) throws ScriptException {
    if (this.servletConfig == null) {
      this.servletConfig = servletConfig;
    }
    try {
      new Thread(() -> {
        try {
          initializeNashorn();
        } catch (ScriptException e) {
          throw new RuntimeException(e);
        }
      }, "Nashorn initialize thread").start();
    } catch (RuntimeException e) {
      if (e.getCause() instanceof ScriptException) {
        throw (ScriptException) e.getCause();
      }
      throw e;
    }
  }

  public String handleJsx(String path, String content, Response response) throws JsxHandlerException {
    try {
      initializeNashorn();

      String scriptLocation = "/";
      int lastIndex = path.lastIndexOf('/');
      if (lastIndex > -1) {
        scriptLocation = path.substring(0, lastIndex);
      }

      String transformed =
        (String) ((ScriptObjectMirror) scriptEngine.invokeMethod(babel, "transform", content, babelConfig)).get("code");

      if (response.shouldWebpack()) {
        String transformedPath = path.replace(".jsx", ".transformed.js");
        writeDebugFileAndReturnPath(transformedPath, transformed);
        Object result = webpackWrapper
          .callMember("compile", transformedPath, scriptLocation, transformed, new JavaFS(scriptEngine, servletConfig.getServletContext(), scriptLocation));
        if (result == null) {
          throw new ScriptException("Failed to get Webpack results for unknown reason");
        }
        Map<String, Object> results = getWebpackResults(result);
        if ((boolean) results.get("hasError")) {
          throw new ScriptException("Failed to get Webpack results: " + results.get("err"));
        }
        transformed = (String) results.get("output");
        writeDebugFileAndReturnPath(transformedPath, transformed);
      }

      if (!response.shouldEval()) {
        return transformed;
      }

      ScriptContext context = new SimpleScriptContext();
      Bindings bindings = new SimpleBindings();
      context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
      bindings.putAll(scriptEngine.getContext().getBindings(ScriptContext.ENGINE_SCOPE));

      SimpleBindings module = new SimpleBindings();
      bindings.put("module", module);
      bindings.put("require", new Require(servletConfig.getServletContext(), scriptLocation, scriptEngine, babel, babelConfig));
      if (response.getJsxResponseContext() != null) {
        bindings.putAll(response.getJsxResponseContext());
      }

      String script = "module.exports = {}; var exports = module.exports; \n\n" + transformed;

      String scriptName = writeDebugFileAndReturnPath(path, script);

      SimpleBindings input = new SimpleBindings();
      input.put("script", script);
      input.put("name", scriptName);
      bindings.put("input", input);
      scriptEngine.eval("load(input);", context);
      Object exports = module.get("exports");

      if (exports != null) {
        if (exports instanceof String) {
          return (String) exports;
        }
        if (exports instanceof ScriptObjectMirror) {
          Object defaultObject = ((ScriptObjectMirror) exports).get("default");
          if (defaultObject != null) {
            return defaultObject.toString();
          }
        } else if (exports instanceof ScriptObject) {
          Object defaultObject = ((ScriptObject) exports).get("default");
          if (defaultObject != null) {
            return defaultObject.toString();
          }
        }
        return exports.toString();
      }
      throw new JsxScriptException(path + " did not export default of value String\n\n" + transformed);
    } catch (ScriptException | NoSuchMethodException e) {
      throw new JsxScriptException(e);
    }

  }

  private Map<String, Object> getWebpackResults(Object result) throws ScriptException {
    Map<String, Object> results = new HashMap<>();
    if (result instanceof ScriptObjectMirror) {
      results.put("hasError", ((ScriptObjectMirror) result).get("hasError"));
      results.put("err", ((ScriptObjectMirror) result).get("err"));
      results.put("output", ((ScriptObjectMirror) result).get("output"));
    } else if (result instanceof ScriptObject) {
      results.put("hasError", ((ScriptObject) result).get("hasError"));
      results.put("err", ((ScriptObject) result).get("err"));
      results.put("output", ((ScriptObject) result).get("output"));
    } else {
      throw new ScriptException("Webpack results is of unknown type: " + result.getClass());
    }
    return results;
  }

  private static Reader read(String path) {
    InputStream in = JsxHandler.class.getClassLoader().getResourceAsStream(path);
    return new InputStreamReader(in);
  }

  private void initializeNashorn() throws ScriptException {
    synchronized (scriptEngineLock) {
      if (scriptEngine == null || !initializeSucceeded) {
        logger.info("Initialising Nashorn script engine...");
        scriptEngine = (NashornScriptEngine) new NashornScriptEngineFactory().getScriptEngine();
        scriptEngine.put("unwrap", new Unwrap());

        loadScript("META-INF/js-server/polyfill.js", "js-server/polyfill.js", true);
        loadScript("META-INF/js-server/js-timeout-polyfill.js", "js-server/js-timeout-polyfill.js", true);
        babel = (ScriptObjectMirror) loadScript("META-INF/js-server/babel.js", "js-server/babel.js", false);
        webpackWrapper = (ScriptObjectMirror) loadScript("META-INF/js-server/webpack-wrapper.js", "js-server/webpack-wrapper.js", false);

        objectConstructor = (JSObject) scriptEngine.eval("Object");
        arrayConstructor = (JSObject) scriptEngine.eval("Array");
        JSObject presets = (JSObject) arrayConstructor.newObject();
        presets.setSlot(0, "react");
        presets.setSlot(1, "es2015");
        babelConfig = (JSObject) objectConstructor.newObject();
        babelConfig.setMember("presets", presets);

        logger.info("Script engine initialized.");
        initializeSucceeded = true;
      }
    }
  }

  private Object loadScript(String path, String name, boolean global) throws ScriptException {

    String script;
    try {
      script = IOUtils.toString(read(path));
    } catch (IOException e) {
      throw new ScriptException(e);
    }
    name = writeDebugFileAndReturnPath(name, script);

    SimpleBindings bindings = new SimpleBindings();
    SimpleBindings input = new SimpleBindings();
    input.put("script", script);
    input.put("name", name);
    bindings.put("input", input);

    if (global) {
      bindings.put("window", scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE));
      return scriptEngine.eval("load(input)", bindings);
    } else {
      ScriptContext scriptContext = new SimpleScriptContext();
      bindings.putAll(scriptEngine.getContext().getBindings(ScriptContext.ENGINE_SCOPE));
      scriptContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
      return scriptEngine.eval("load(input)", scriptContext);
    }
  }

  private String writeDebugFileAndReturnPath(String path, String content) throws ScriptException {
    if (DEBUG_JS_PATH != null) {
      String debugJsPath = DEBUG_JS_PATH;
      if (debugJsPath.startsWith("/")) {
        debugJsPath = debugJsPath.substring(1);
      }
      File debugPathFile = new File(debugJsPath);
      if (!debugPathFile.exists()) {
        //noinspection ResultOfMethodCallIgnored
        debugPathFile.mkdirs();
      } else if (!debugPathFile.isDirectory()) {
        throw new IllegalArgumentException("timewise.debugJsPath " + debugPathFile.getAbsolutePath() + " is not a directory");
      }
      File debugFile = new File(debugPathFile, path);
      try {
        if (debugFile.exists()) {
          //noinspection ResultOfMethodCallIgnored
          debugFile.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        debugFile.getParentFile().mkdirs();
        //noinspection ResultOfMethodCallIgnored
        debugFile.createNewFile();
        FileOutputStream fileOutputStream = new FileOutputStream(debugFile);
        IOUtils.write(content, fileOutputStream, Charset.defaultCharset());
        return debugFile.getPath();
      } catch (IOException e) {
        throw new ScriptException(e);
      }

    } else {
      return path;
    }
  }
}
