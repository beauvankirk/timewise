package io.squark.yggdrasil.jsx.handler;

import io.squark.yggdrasil.jsx.servlet.JsxServlet;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.runtime.ECMAException;
import jdk.nashorn.internal.runtime.ScriptObject;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2017-01-01.
 * Copyright 2017
 * <p>
 * HEAVILY INSPIRED BY https://github.com/coveo/nashorn-commonjs-modules
 */
public class Require implements RequireInterface {

  private static final Logger logger = LoggerFactory.getLogger(Require.class);
  private static final Map<String, Object> cache = new HashMap<>();
  private static final boolean DISABLE_CACHE = JsxServlet.DISABLE_CACHE;
  private static Unwrap unwrap = new Unwrap();

  private ServletContext servletContext;
  private String basePath;
  private NashornScriptEngine engine;
  private ScriptObjectMirror babel;
  private JSObject babelConfig;

  public Require(ServletContext servletContext, String basePath, NashornScriptEngine engine, ScriptObjectMirror babel,
    JSObject babelConfig) {
    this.servletContext = servletContext;
    this.basePath = basePath;
    this.engine = engine;
    this.babel = babel;
    this.babelConfig = babelConfig;
  }

  @Override
  public Object require(String module) throws ScriptException {
    Object result = internalRequire(module);
    if (result == null) {
      throwModuleNotFoundException(module);
    }
    return result;
  }

  private Object internalRequire(String module) throws ScriptException {
    Path path;
    if (module.startsWith(".") || module.startsWith("/")) {
      path = Paths.get((basePath + "/" + module).replaceAll("////", "/")).normalize();
    } else {
      path = Paths.get(module).normalize();
    }
    Object result;
    result = cache.get(path.toString());
    if (result != null) {
      return result;
    }
    String fileName = path.getFileName().toString();
    String extension = FilenameUtils.getExtension(fileName);
    InputStream inputStream = servletContext.getResourceAsStream(path.toString());
    if (inputStream != null) {
      if (!extension.isEmpty()) {
        switch (extension) {
          case "js":
            result = handleJs(inputStream, module);
            break;
          case "json":
            result = handlePackageJson(inputStream, module);
            break;
          case "jsx":
            result = handleJsx(inputStream, module);
            break;
          default:
            throwUnknownModuleTypeException(module);
        }
      }
    } else if (extension.isEmpty()) {
      result = internalRequire(module + ".js");
      if (result == null) {
        result = internalRequire(module + ".json");
      }
      if (result == null) {
        result = internalRequire(module + ".jsx");
      }
      if (result == null) {
        result = internalRequire(Paths.get("node_modules", module + ".js").toString());
      }
      if (result == null) {
        result = internalRequire(Paths.get("node_modules", module + ".json").toString());
      }
      if (result == null) {
        result = internalRequire(Paths.get("node_modules", module + ".jsx").toString());
      }
      if (result == null) {
        result = requireFolder(path, module);
      }
      if (result == null) {
        result = requireFolder(Paths.get("node_modules", path.toString()), module);
      }
    }
    if (result != null && !DISABLE_CACHE) {
      cache.put(path.toString(), result);
    }
    return result;
  }

  private Object requireFolder(Path path, String module) throws ScriptException {
    InputStream inputStream = servletContext.getResourceAsStream(path.toString() + "/package.json");
    if (inputStream != null) {
      return handlePackageJson(inputStream, module);
    }
    inputStream = servletContext.getResourceAsStream(path.toString() + "/index.js");
    if (inputStream == null) {
      inputStream = servletContext.getResourceAsStream(path.toString() + "/" + module + ".js");
    }
    if (inputStream != null) {
      return handleJs(inputStream, module);
    }
    inputStream = servletContext.getResourceAsStream(path.toString() + "/index.jsx");
    if (inputStream == null) {
      inputStream = servletContext.getResourceAsStream(path.toString() + "/" + module + ".jsx");
    }
    if (inputStream != null) {
      return handleJsx(inputStream, module);
    }
    return null;
  }

  private void throwUnknownModuleTypeException(String module) throws ScriptException {
    ScriptObjectMirror ctor = (ScriptObjectMirror) engine.eval("Error");
    Bindings error = (Bindings) ctor.newObject("Unknown module type: " + module);
    error.put("code", "UNKNOWN_MODULE_TYPE");
    throw new ECMAException(error, null);
  }

  private Object handleJsx(InputStream inputStream, String module) throws ScriptException {
    try {
      String code = IOUtils.toString(inputStream, Charset.defaultCharset());
      String transformed = (String) ((ScriptObjectMirror) engine.invokeMethod(babel, "transform", code, babelConfig)).get("code");

      return handleScript(transformed, module);
    } catch (NoSuchMethodException | IOException e) {
      throw new ScriptException(e);
    }
  }

  private Object handlePackageJson(InputStream inputStream, String module) {
    return null;
  }

  private Object handleJs(InputStream inputStream, String module) throws ScriptException {
    try {
      String code = IOUtils.toString(inputStream, Charset.defaultCharset());
      return handleScript(code, module);
    } catch (IOException e) {
      throw new ScriptException(e);
    }
  }

  private Object handleScript(String code, String module) throws ScriptException {
    //logger.debug(module + ":\n\n" + code);

    ScriptContext context = new SimpleScriptContext();
    Bindings bindings = new SimpleBindings();
    context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
    bindings.putAll(engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE));

    Bindings result = new SimpleBindings();
    bindings.put("module", result);
    bindings.put("require", this);

    String script = "module.exports = {}; var exports = module.exports; \n" + code;
    String scriptName = module;
    if (JsxHandler.DEBUG_JS_PATH != null) {
      String debugJsPath = JsxHandler.DEBUG_JS_PATH;
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
      File debugFile = new File(debugPathFile, module.endsWith(".js") ? module : module + ".transformed.js");
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
        throw new RuntimeException(e);
      }
    }

    SimpleBindings input = new SimpleBindings();
    input.put("script", script);
    input.put("name", scriptName.replace("/./", "/"));
    bindings.put("input", input);
    engine.eval(" load(input);", context);

    Object exports = unwrap.unwrap(result.get("exports"));
    if (exports instanceof ScriptObject && ((ScriptObject) exports).has("default")) {
      ((ScriptObject) exports).put("default", unwrap.unwrap(((ScriptObject) exports).get("default")), true);
    }

    return exports;
  }

  private void throwModuleNotFoundException(String module) throws ScriptException {
    ScriptObjectMirror ctor = (ScriptObjectMirror) engine.eval("Error");
    Bindings error = (Bindings) ctor.newObject("Module not found: " + module);
    error.put("code", "MODULE_NOT_FOUND");
    throw new ECMAException(error, null);
  }
}
