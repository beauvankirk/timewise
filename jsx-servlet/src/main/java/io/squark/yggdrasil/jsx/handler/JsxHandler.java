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
  private static volatile NashornScriptEngine scriptEngine = null;
  private static volatile JSObject objectConstructor;
  private static volatile JSObject arrayConstructor;
  private static volatile JSObject babelConfig;
  private static boolean initializeSucceeded = false;
  private ServletConfig servletConfig;
  private static final String polyfill;

  static {
    try {
      polyfill = IOUtils.toString(Require.class.getClassLoader().getResource("META-INF/js-server/polyfill.js"), Charset.defaultCharset());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

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
      }).start();
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

      String transformed =
        (String) ((ScriptObjectMirror) scriptEngine.invokeMethod(babel, "transform", content, babelConfig)).get("code");
      //logger.debug(transformed);

      String scriptLocation = "/";
      int lastIndex = path.lastIndexOf('/');
      if (lastIndex > -1) {
        scriptLocation = path.substring(0, lastIndex);
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

      String script = "module.exports = {}; var exports = module.exports; \n\n" + polyfill + "\n" + transformed;
      String scriptName = path;
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
          IOUtils.write(script, fileOutputStream, Charset.defaultCharset());
          scriptName = debugFile.getPath();
        } catch (IOException e) {
          throw new JsxScriptException(e);
        }
      }
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
        scriptEngine.eval(read("META-INF/js-server/polyfill.js"));
        babel = (ScriptObjectMirror) scriptEngine.eval(read("META-INF/js-server/babel.js"));
        objectConstructor = (JSObject) scriptEngine.eval("Object");
        arrayConstructor = (JSObject) scriptEngine.eval("Array");
        JSObject presets = (JSObject) arrayConstructor.newObject();
        presets.setSlot(0, "react");
        presets.setSlot(1, "es2015");
        babelConfig = (JSObject) objectConstructor.newObject();
        babelConfig.setMember("presets", presets);

        initializeSucceeded = true;
        logger.info("Script engine initialized.");
      }
    }
  }
}
