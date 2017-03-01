package io.squark.yggdrasil.jsx.handler;

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.NodeJS;
import com.eclipsesource.v8.ReferenceHandler;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Locker;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8Value;
import com.eclipsesource.v8.utils.V8ObjectUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import org.slf4j.MarkerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;
import javax.servlet.ServletConfig;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
  private static final Map<String, String> findPathCache = new HashMap<>();
  private static final Map<String, String> readPackageCache = new HashMap<>();
  private static volatile ScriptObjectMirror babel = null;
  private static volatile ScriptObjectMirror webpackWrapper = null;
  private static volatile NashornScriptEngine scriptEngine = null;
  private static volatile JSObject objectConstructor;
  private static volatile JSObject arrayConstructor;
  private static volatile JSObject babelConfig;
  private static boolean initializeSucceeded = false;
  private static boolean warnFindPathOnce = false;
  private static Gson gson = new Gson();
  private static NodeJSWrapper nodeJS;
  private static V8 v8Runtime;
  private static V8Object babelOptions;
  private static V8Locker v8Locker;
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
      initializeNashorn();
    } catch (RuntimeException e) {
      if (e.getCause() instanceof ScriptException) {
        throw (ScriptException) e.getCause();
      }
      throw e;
    }
  }

  public String handleJsx(String path, String content, Response response) throws JsxHandlerException {
    synchronized (v8Locker) {
      JsxReferenceHandler jsxReferenceHandler = createReferenceHandler();
      try {
        initializeNashorn();
        v8Locker.acquire();

        String scriptLocation = "/";
        int lastIndex = path.lastIndexOf('/');
        if (lastIndex > -1) {
          scriptLocation = path.substring(0, lastIndex);
        }

        if (response.shouldWebpack()) {
          String transformed;
          V8Object webpack = nodeJS.require("/webpack2.js");
          V8Array webpackArgs = new V8Array(v8Runtime).push("." + path);
          boolean finished = false;
          V8Object resultsObject = new V8Object(v8Runtime);
          webpackArgs.push(resultsObject);
          webpack.executeVoidFunction("compile", webpackArgs);
          while (nodeJS.isRunning()) {
            nodeJS.handleMessage();
          }
          transformed = "lala"; //result.getString("output");
          String transformedPath = path.replace(".jsx", ".transformed.js");
          writeDebugFileAndReturnPath(transformedPath, transformed);
          webpackArgs.release();
          webpack.release();
          //result.release();
          return transformed;
        }

        V8Object result = nodeJS.require(path);
        Object exportDefault = result.get("default");
        if (exportDefault instanceof String) {
          return (String) exportDefault;
        } else {
          throw new JsxHandlerException(path + " did not have a default export of String type. Was " +
                                        (exportDefault == null ? "null" : V8.getStringRepresentaion(result.getType("default"))));
        }
      } catch (ScriptException e) {
        throw new JsxHandlerException(e);
      } finally {
        jsxReferenceHandler.checkClean();
        v8Locker.release();
      }
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

  private Reader read(String path) {
    InputStream in = servletConfig.getServletContext().getResourceAsStream(path);
    if (in == null) {
      return null;
    }
    return new InputStreamReader(in);
  }

  private void initializeNashorn() throws ScriptException {
    synchronized (scriptEngineLock) {
      if (!initializeSucceeded) {
        System.setProperty("NODE_PATH", "/bajs");
        V8.setFlags("NODE_PATH=/ NODE_DEBUG=module");
        logger.info("Initialising Node.js script engine...");
        nodeJS = new NodeJSWrapper(NodeJS.createNodeJS());
        v8Runtime = nodeJS.getRuntime();
        v8Locker = v8Runtime.getLocker();

        babelOptions = new V8Object(v8Runtime).add("presets", new V8Array(v8Runtime).push("react").push("es2015"));

        try {
          overloadNodeFsAndModule(nodeJS);
        } catch (JsxScriptException e) {
          e.printStackTrace();
        }

        nodeJS.require("/webpack2.js");

        logger.info("Script engine initialized.");
        v8Locker.release();
        initializeSucceeded = true;
      }
    }
  }

  private Void makeAsync(JavaCallback function, V8Array arguments, V8Function callback) {
    if (callback == null) {
      throw new IllegalArgumentException("Callback must not be null");
    }
    V8Function async = new V8Function(v8Runtime, (receiver, parameters) -> {
      V8Array results = new V8Array(v8Runtime);
      V8Array err = null;
      V8Function v8Function = new V8Function(v8Runtime, function);
      try {
        Object data = v8Function.call(null, arguments);
        results.pushNull();
        V8ObjectUtils.pushValue(v8Runtime, results, data);
        callback.call(null, results);
      } catch (Exception e) {
        err = new V8Array(v8Runtime).push(e.getMessage());
        results.push(err);
        callback.call(null, results);
      } finally {
        safeRelease(v8Function, err, results);
      }
      return null;
    });
    V8Function setImmediate = (V8Function) v8Runtime.getObject("setImmediate");
    V8Array setImmediateArgs = new V8Array(v8Runtime).push(async).push(callback);
    setImmediate.call(null, setImmediateArgs);
    setImmediateArgs.release();
    setImmediate.release();
    return null;
  }

  private JsxReferenceHandler createReferenceHandler() {
    JsxReferenceHandler handler = new JsxReferenceHandler();
    v8Runtime.addReferenceHandler(handler);
    return handler;
  }

  private void overloadNodeFsAndModule(NodeJSWrapper nodeJS) throws JsxScriptException {

    JsxReferenceHandler jsxReferenceHandler = createReferenceHandler();

    V8Object fs = nodeJS.require("fs");
    V8Function readFileSyncOrig = (V8Function) fs.get("readFileSync");
    fs.add("_readFileSyncOrig", readFileSyncOrig);
    V8Function readFileOrig = (V8Function) fs.get("readFile");
    fs.add("_readFileOrig", readFileOrig);
    V8Function statOrig = (V8Function) fs.get("stat");
    fs.add("_statOrig", statOrig);
    V8Function statSyncOrig = (V8Function) fs.get("statSync");
    fs.add("_statSyncOrig", statSyncOrig);
    V8Function readdirSyncOrig = (V8Function) fs.get("readdirSync");
    fs.add("_readdirSyncOrig", readdirSyncOrig);
    V8Function readdirOrig = (V8Function) fs.get("readdir");
    fs.add("_readdirOrig", readdirOrig);
    readFileSyncOrig.release();
    readFileOrig.release();
    statOrig.release();
    statSyncOrig.release();
    readdirSyncOrig.release();
    readdirOrig.release();

    V8Function falseFn = new V8Function(nodeJS.getRuntime(), (receiver, parameters) -> false);
    V8Function trueFn = new V8Function(nodeJS.getRuntime(), (receiver, parameters) -> true);

    JavaCallback readdirSync = (receiver, parameters) -> {
      V8Object tempFs = null;
      try {
        logger.debug("readdirSync: " + parameters.getString(0));
        tempFs = nodeJS.require("fs");
        return ((V8Function) tempFs.get("_readdirSyncOrig")).call(receiver, parameters);
      } finally {
        if (tempFs != null) {
          tempFs.release();
        }
      }
    };
    fs.registerJavaMethod(readdirSync, "readdirSync");

    JavaCallback readdir = (receiver, parameters) -> {
      V8Object tempFs = null;
      try {
        logger.debug("readdir: " + parameters.getString(0));
        tempFs = nodeJS.require("fs");
        return ((V8Function) tempFs.get("_readdirOrig")).call(receiver, parameters);
      } finally {
        if (tempFs != null) {
          tempFs.release();
        }
      }
    };
    fs.registerJavaMethod(readdir, "readdir");

    JavaCallback statSync = (receiver, parameters) -> {
      try {
        V8Object tempFs = null;
        try {
          logger.debug("statSync: " + parameters.getString(0));
          tempFs = nodeJS.require("fs");
          return ((V8Function) tempFs.get("_statSyncOrig")).call(receiver, parameters);
        } finally {
          if (tempFs != null) {
            tempFs.release();
          }
        }
      } catch (Exception origError) {
        try {
          String origPath = parameters.getString(0);
          String path = origPath;
          if (!path.startsWith("/")) {
            path = '/' + path;
          }
          path = Paths.get(path).normalize().toString();
          URL url = servletConfig.getServletContext().getResource(path);
          if (url == null) {
            if (!path.endsWith("/")) {
              path += '/';
              url = servletConfig.getServletContext().getResource(path);
            }
            if (url == null) {
              throw (FileNotFoundException) new FileNotFoundException(origPath).initCause(origError);
            }
          }
          V8Object statResult = new V8Object(nodeJS.getRuntime());
          boolean isDirectory = path.endsWith("/");
          statResult.add("isFile", isDirectory ? falseFn : trueFn);
          statResult.add("isDirectory", isDirectory ? trueFn : falseFn);
          statResult.add("isBlockDevice", falseFn);
          statResult.add("isCharacterDevice", falseFn);
          statResult.add("isSymbolicLink", falseFn);
          statResult.add("isFIFO", falseFn);
          statResult.add("isSocket", falseFn);
          statResult.add("__resultPath", path);
          return statResult;
        } catch (MalformedURLException | FileNotFoundException e) {
          throw new RuntimeException(e);
        }
      }
    };
    fs.registerJavaMethod(statSync, "statSync");

    JavaCallback stat = (receiver, parameters) -> {
      V8Array arguments = new V8Array(v8Runtime);
      V8Function callback = null;
      try {
        for (int i = 0; i < parameters.length(); i++) {
          if (i == parameters.length() - 1) {
            callback = (V8Function) parameters.getObject(i);
          } else {
            V8ObjectUtils.pushValue(v8Runtime, arguments, parameters.get(i));
          }
        }
        return makeAsync(statSync, arguments, callback);
      } finally {
        safeRelease(arguments, callback);
      }
    };
    fs.registerJavaMethod(stat, "stat");

    JavaCallback readFileSync = (receiver, parameters) -> {
      try {
        V8Object tempFs = null;
        try {
          logger.debug("readFileSync: " + parameters.get(0) + ", " + parameters.get(1));
          tempFs = nodeJS.require("fs");
          return ((V8Function) tempFs.get("_readFileSyncOrig")).call(receiver, parameters);
        } finally {
          safeRelease(tempFs);
        }
      } catch (Exception origError) {
        V8Array statArgs = new V8Array(nodeJS.getRuntime());
        statArgs.push(parameters.getString(0));
        V8Object st = (V8Object) statSync.invoke(null, statArgs);
        statArgs.release();

        Object options = parameters.get(1);
        V8Object optionsObject = new V8Object(nodeJS.getRuntime());
        if (options instanceof V8Object) {
          optionsObject = (V8Object) options;
        } else if (options instanceof String) {
          optionsObject.add("encoding", (String) options);
        }
        try {
          URL url = servletConfig.getServletContext().getResource(st.getString("__resultPath"));
          if (url == null) {
            throw (FileNotFoundException) new FileNotFoundException(st.getString("__resultPath")).initCause(origError);
          }
          String charset = optionsObject.getString("encoding");
          if (charset == null) {
            charset = Charset.defaultCharset().name();
          }
          String content = IOUtils.toString(url.openStream(), charset);
          if (parameters.getString(0).startsWith("/node_modules")) {
            return content;
          } else {
            return nodeJS.require("babel-standalone")
              .executeObjectFunction("transform", new V8Array(v8Runtime).push(content).push(babelOptions)).getString("code");
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        } finally {
          st.release();
          optionsObject.release();
        }

      }
    };
    fs.registerJavaMethod(readFileSync, "readFileSync");

    JavaCallback readFile = (receiver, parameters) -> {
      logger.debug("readFile: " + parameters.getString(0));
      V8Array arguments = new V8Array(v8Runtime);
      V8Function callback = null;
      try {
        for (int i = 0; i < parameters.length(); i++) {
          if (i == parameters.length() - 1) {
            callback = (V8Function) parameters.getObject(i);
          } else {
            V8ObjectUtils.pushValue(v8Runtime, arguments, parameters.get(i));
          }
        }
        return makeAsync(readFileSync, arguments, callback);
      } finally {
        safeRelease(arguments, callback);
      }
    };
    fs.registerJavaMethod(readFile, "readFile");

    V8Function module = (V8Function) nodeJS.require("module");
    V8Function _findPathOrig = (V8Function) module.get("_findPath");
    module.add("__findPathOrig", _findPathOrig);
    _findPathOrig.release();

    JavaCallback _findPath = (receiver, parameters) -> {
      Object found = null;
      V8Object tempModule = null;
      Exception origError = null;
      try {
        logger.debug("_findPath: " + parameters.getString(0) + ", " + parameters.get(1));
        tempModule = nodeJS.require("module");
        found = ((V8Function) tempModule.get("__findPathOrig")).call(receiver, parameters);
      } catch (Exception e) {
        origError = e;
      } finally {
        safeRelease(tempModule);
      }
      if (found == null || (found instanceof Boolean && !((Boolean) found))) {
        String request = parameters.getString(0);
        Object paths = parameters.get(1);
        if (paths instanceof V8Array) {
          paths = V8ObjectUtils.getTypedArray((V8Array) paths, V8Array.STRING);
        }
        if (Paths.get(request).isAbsolute()) {
          paths = new String[]{""};
        } else if (paths == null || paths == V8.getUndefined() || !paths.getClass().isArray() || Array.getLength(paths) == 0) {
          return false;
        }
        String cacheKey = request;
        for (Object path : (Object[]) paths) {
          cacheKey += ":" + path;
        }
        String filename = findPathCache.get(cacheKey);
        if (filename != null) {
          return filename;
        }
        String[] exts = null;
        boolean trailingSlash = request.length() > 0 && request.charAt(request.length() - 1) == 47;

        // For each path
        for (int i = 0; i < Array.getLength(paths); i++) {
          // Don't search further if path doesn't exist
          String curPath = String.valueOf(Array.get(paths, i));
          if (curPath != null && stat(curPath) < 1) {
            //curPath + request doesn't exist.
            continue;
          }
          String basePath = resolve(curPath, request);
          filename = null;

          int rc = stat(basePath);
          if (!trailingSlash) {
            if (rc == 0) {  // File.
              filename = resolve(basePath);
            } else if (rc == 1) {  // Directory.
              if (exts == null) exts = ((V8Object) receiver.get("_extensions")).getKeys();
              filename = tryPackage(basePath, exts);
            }
            if (filename == null) {
              // try it with each of the extensions
              if (exts == null) exts = ((V8Object) receiver.get("_extensions")).getKeys();
              filename = tryExtensions(basePath, exts);
            }
          }

          if (filename == null && rc == 1) {  // Directory.
            if (exts == null) exts = ((V8Object) receiver.get("_extensions")).getKeys();
            filename = tryPackage(basePath, exts);
          }

          if (filename == null && rc == 1) {  // Directory.
            // try it with each of the extensions at "index"
            if (exts == null) exts = ((V8Object) receiver.get("_extensions")).getKeys();
            filename = tryExtensions(resolve(basePath, "index"), exts);
          }

          if (filename != null) {
            // Warn once if '.' resolved outside the module dir
            if (Objects.equals(request, ".") && i > 0) {
              if (!warnFindPathOnce) {
                warnFindPathOnce = true;
                logger.warn(MarkerFactory.getMarker("DeprecationWarning"),
                  "warning: require('.') resolved outside the package " + "directory. This functionality is deprecated and " +
                  "will be removed " + "soon.");
              }
            }

            findPathCache.put(cacheKey, filename);
            return filename;
          }
        }
        return null;
      }
      return found;
    };
    module.registerJavaMethod(_findPath, "_findPath");

    V8Object _extensions = module.getObject("_extensions");
    V8Object _js = _extensions.getObject(".js");
    _extensions.add(".jsx", _js);

    safeRelease(_js, _extensions, fs, module);

    jsxReferenceHandler.checkClean();
  }

  private void safeRelease(V8Object... objects) {
    for (V8Object object : objects) {
      if (object != null) {
        object.release();
      }
    }
  }

  private String tryFile(String requestPath) {
    int rc = stat(requestPath);
    return rc == 0 ? resolve(requestPath) : null;
  }

  private String tryExtensions(String path, String[] exts) {
    for (int i = 0; i < exts.length; i++) {
      String filename = tryFile(path + exts[i]);
      if (filename != null) {
        return filename;
      }
    }
    return null;
  }

  private String tryPackage(String requestPath, String[] exts) {
    String pkg = readPackage(requestPath);

    if (pkg == null) return null;

    String filename = resolve(requestPath, pkg);
    String result = tryFile(filename);
    if (result == null) {
      result = tryExtensions(filename, exts);
      if (result == null) {
        result = tryExtensions(resolve(filename, "index"), exts);
      }
    }
    return result;
  }

  private String readPackage(String requestPath) {
    String pkg = readPackageCache.get(requestPath);
    if (pkg != null) {
      return pkg;
    }

    String jsonPath = resolve(requestPath, "package.json");
    Reader json = read(jsonPath);

    if (json == null) {
      return null;
    }

    try {
      JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
      if (jsonObject.get("main") != null) {
        pkg = jsonObject.get("main").getAsString();
        readPackageCache.put(requestPath, pkg);
      }
      return pkg;
    } catch (JsonParseException e) {
      throw new RuntimeException("Error parsing " + jsonPath, e);
    }
  }

  private String resolve(String... arguments) {
    String resolvedPath = "";
    boolean resolvedAbsolute = false;

    for (int i = arguments.length - 1; i >= -1 && !resolvedAbsolute; i--) {
      String path;
      if (i >= 0) path = arguments[i];
      else {
        path = "/";
      }

      // Skip empty entries
      if (path.length() == 0) {
        continue;
      }

      resolvedPath = path + '/' + resolvedPath;
      resolvedAbsolute = path.charAt(0) == 47;
    }

    Path p = Paths.get(resolvedPath);
    if (resolvedAbsolute) {
      resolvedPath = p.toAbsolutePath().normalize().toString();
    } else {
      resolvedPath = p.normalize().toString();
    }

    if (resolvedAbsolute) {
      if (resolvedPath.length() > 0) if (!resolvedPath.startsWith("/")) {
        return '/' + resolvedPath;
      } else {
        return resolvedPath;
      }
      else return "/";
    } else if (resolvedPath.length() > 0) {
      return resolvedPath;
    } else {
      return ".";
    }
  }

  //1: directory, 0: file, -1: doesn't exist
  private int stat(String filename) {
    if (!filename.startsWith("/")) {
      filename = "/" + filename;
    }
    try {
      URL url;
      if ((url = servletConfig.getServletContext().getResource(filename)) != null) {
        return url.toString().endsWith("/") ? 1 : 0;
      }
      if (servletConfig.getServletContext().getResource(filename + "/") != null) {
        return 1;
      }
    } catch (MalformedURLException e) {
      //NOP
    }
    return -1;
  }


  private void oldinitializeNashorn() throws ScriptException {
    synchronized (scriptEngineLock) {
      if (scriptEngine == null || !initializeSucceeded) {
        logger.info("Initialising Nashorn script engine...");
        scriptEngine = (NashornScriptEngine) new NashornScriptEngineFactory().getScriptEngine();
        scriptEngine.put("unwrap", new Unwrap());

        objectConstructor = (JSObject) scriptEngine.eval("Object");
        arrayConstructor = (JSObject) scriptEngine.eval("Array");

        loadScript("META-INF/js-server/polyfill.js", "js-server/polyfill.js", true);
        loadScript("META-INF/js-server/js-timeout-polyfill.js", "js-server/js-timeout-polyfill.js", true);

        SimpleBindings webpackWorkaround = new SimpleBindings();
        webpackWorkaround.put("javaFS", new JavaFS(scriptEngine, servletConfig.getServletContext(), "/"));
        webpackWrapper =
          (ScriptObjectMirror) loadScript("META-INF/js-server/webpack-wrapper.js", "js-server/webpack-wrapper.js", false,
            webpackWorkaround);

        babel = (ScriptObjectMirror) loadScript("META-INF/js-server/babel.js", "js-server/babel.js", false);
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

  private Object loadScript(String path, String name, boolean global, SimpleBindings... extraBindings) throws ScriptException {

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
    for (SimpleBindings b : extraBindings) {
      bindings.putAll(b);
    }

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

  private class JsxReferenceHandler implements ReferenceHandler {

    private List<Reference> refs = new ArrayList<>();

    @Override
    public void v8HandleCreated(V8Value object) {
      refs.add(new Reference(object));
    }

    @Override
    public void v8HandleDisposed(V8Value object) {
      refs.remove(new Reference(object, false));
    }

    public void checkClean() {
      if (refs.size() > 0) {
        throw new LeakedJsResourceException(refs.toString());
      }
    }

    private class Reference {

      private V8Value object;
      private StackTraceElement[] stackTraceElements;

      public Reference(V8Value object, boolean includeStack) {
        this.object = object;
        if (includeStack) {
          stackTraceElements = new Exception().getStackTrace();
        }
      }

      public Reference(V8Value object) {
        this(object, logger.isDebugEnabled());
      }

      @Override
      public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Reference reference = (Reference) o;

        return Objects.equals(object, reference.object);
      }

      @Override
      public int hashCode() {
        return object != null ? object.hashCode() : 0;
      }

      @Override
      public String toString() {
        String stackTraceString = "";
        if (stackTraceElements != null) {
          StringWriter stringWriter = new StringWriter();
          for (StackTraceElement s : stackTraceElements) {
            stringWriter.append("\tat ").append(s.toString()).append("\n");
          }
          stackTraceString = ", stackTrace:\n" + stringWriter.toString() + "\n";
        }
        return "Reference{" + "object=" + object + stackTraceString + '}';
      }
    }
  }
}
