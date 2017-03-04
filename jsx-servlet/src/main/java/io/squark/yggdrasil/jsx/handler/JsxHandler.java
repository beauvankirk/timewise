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
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.servlet.ServletConfig;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
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
  private static boolean initializeSucceeded = false;
  private static boolean warnFindPathOnce = false;
  private static Gson gson = new Gson();
  private static NodeJSWrapper nodeJS;
  private static V8 v8Runtime;
  private static V8Locker v8Locker;
  private ServletConfig servletConfig;

  @Inject
  @JsxServletConfig
  public void setServletConfig(@JsxServletConfig ServletConfig servletConfig) {
    this.servletConfig = servletConfig;
  }

  public void initialize(@Observes ServletConfig servletConfig) {
    if (this.servletConfig == null) {
      this.servletConfig = servletConfig;
    }
    initializeV8();
  }

  public String handleJsx(String path, URL file, Response response) throws JsxHandlerException {
    synchronized (v8Locker) {
      JsxReferenceHandler jsxReferenceHandler = createReferenceHandler();
      try {
        initializeV8();
        v8Locker.acquire();

        if (response.shouldEval()) {
          nodeJS.invalidate(path);
          V8Object result = nodeJS.require(path);
          Object exportDefault = result.get("default");
          if (exportDefault instanceof String) {
            return (String) exportDefault;
          } else {
            throw new JsxHandlerException(path + " did not have a default export of String type. Was " +
                                          (exportDefault == null ? "null" : V8.getStringRepresentaion(result.getType("default"))));
          }
        } else {
          return IOUtils.toString(file, Charset.defaultCharset());
        }
      } catch (Exception e) {
        if (e instanceof JsxHandlerException) {
          throw (JsxHandlerException) e;
        }
        throw new JsxHandlerException(e);
      } finally {
        try {
          jsxReferenceHandler.releaseAll();
          v8Locker.release();
        } catch (Exception e) {
          if (v8Locker.hasLock()) {
            v8Locker.release();
          }
        }
      }
    }
  }

  private Reader read(String path) {
    InputStream in = servletConfig.getServletContext().getResourceAsStream(path);
    if (in == null) {
      return null;
    }
    return new InputStreamReader(in);
  }

  private void initializeV8() {
    synchronized (scriptEngineLock) {
      if (!initializeSucceeded) {
        System.setProperty("NODE_PATH", "/bajs");
        V8.setFlags("NODE_PATH=/ NODE_DEBUG=module");
        logger.info("Initialising Node.js script engine...");
        nodeJS = new NodeJSWrapper(NodeJS.createNodeJS());
        v8Runtime = nodeJS.getRuntime();
        v8Locker = v8Runtime.getLocker();

        try {
          overloadNodeFsAndModule(nodeJS);
        } catch (JsxScriptException e) {
          e.printStackTrace();
        }

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

    v8Runtime.registerJavaMethod((receiver, parameters) -> false, "_falseFn");
    v8Runtime.registerJavaMethod((receiver, parameters) -> true, "_trueFn");

    JavaCallback readdirSync = (receiver, parameters) -> {
      logger.debug("readdirSync: " + parameters.getString(0));
      V8Object tempFs = nodeJS.require("fs");
      return ((V8Function) tempFs.get("_readdirSyncOrig")).call(receiver, parameters);

    };
    fs.registerJavaMethod(readdirSync, "readdirSync");

    JavaCallback readdir = (receiver, parameters) -> {
      logger.debug("readdir: " + parameters.getString(0));
      V8Object tempFs = nodeJS.require("fs");
      return ((V8Function) tempFs.get("_readdirOrig")).call(receiver, parameters);

    };
    fs.registerJavaMethod(readdir, "readdir");

    JavaCallback statSync = (receiver, parameters) -> {
      try {
        logger.debug("statSync: " + parameters.getString(0));
        V8Object tempFs = nodeJS.require("fs");
        return ((V8Function) tempFs.get("_statSyncOrig")).call(receiver, parameters);
      } catch (Exception origError) {
        V8Function falseFn = (V8Function) v8Runtime.getObject("_falseFn");
        V8Function trueFn = (V8Function) v8Runtime.getObject("_trueFn");
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
      for (int i = 0; i < parameters.length(); i++) {
        if (i == parameters.length() - 1) {
          callback = (V8Function) parameters.getObject(i);
        } else {
          V8ObjectUtils.pushValue(v8Runtime, arguments, parameters.get(i));
        }
      }
      return makeAsync(statSync, arguments, callback);

    };
    fs.registerJavaMethod(stat, "stat");

    JavaCallback readFileSync = (receiver, parameters) -> {
      try {
        logger.debug("readFileSync: " + parameters.get(0) + ", " + parameters.get(1));
        V8Object tempFs = nodeJS.require("fs");
        return ((V8Function) tempFs.get("_readFileSyncOrig")).call(receiver, parameters);
      } catch (Exception origError) {
        V8Array statArgs = new V8Array(nodeJS.getRuntime());
        statArgs.push(parameters.getString(0));
        V8Object st = (V8Object) statSync.invoke(null, statArgs);
        statArgs.release();

        Object options = parameters.get(1);
        V8Object optionsObject;
        if (options instanceof V8Object) {
          optionsObject = (V8Object) options;
        } else if (options instanceof String) {
          optionsObject = new V8Object(nodeJS.getRuntime());
          optionsObject.add("encoding", (String) options);
        } else {
          optionsObject = new V8Object(nodeJS.getRuntime());
        }
        try {
          URL url = servletConfig.getServletContext().getResource(st.getString("__resultPath"));
          if (url == null) {
            throw (FileNotFoundException) new FileNotFoundException(st.getString("__resultPath")).initCause(origError);
          }
          String charset = null;
          if (!optionsObject.isUndefined()) {
            Object charsetObject = optionsObject.get("encoding");
            if (charsetObject instanceof String) {
              charset = (String) charsetObject;
            } else if (charsetObject instanceof V8Object) {
              Object innerObject = ((V8Object) charsetObject).get("encoding");
              if (innerObject != null && innerObject instanceof String) {
                charset = (String) innerObject;
              }
            }
          }
          if (charset == null) {
            charset = Charset.defaultCharset().name();
          }
          String content = IOUtils.toString(url.openStream(), charset);
          if (parameters.getString(0).startsWith("/node_modules") || !parameters.getString(0).endsWith(".jsx")) {
            return content;
          } else {
            V8Object babelOptions = new V8Object(v8Runtime).add("presets", new V8Array(v8Runtime).push("react").push("es2015"));
            return nodeJS.require("babel-standalone")
              .executeObjectFunction("transform", new V8Array(v8Runtime).push(content).push(babelOptions)).getString("code");
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
    fs.registerJavaMethod(readFileSync, "readFileSync");

    JavaCallback readFile = (receiver, parameters) -> {
      logger.debug("readFile: " + parameters.getString(0));
      V8Array arguments = new V8Array(v8Runtime);
      V8Function callback = null;
      for (int i = 0; i < parameters.length(); i++) {
        if (i == parameters.length() - 1) {
          callback = (V8Function) parameters.getObject(i);
        } else {
          V8ObjectUtils.pushValue(v8Runtime, arguments, parameters.get(i));
        }
      }
      return makeAsync(readFileSync, arguments, callback);

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

    jsxReferenceHandler.releaseAll();
  }

  private void safeRelease(V8Object... objects) {
    for (V8Object object : objects) {
      if (object != null && !object.isReleased()) {
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

  private String writeDebugFileAndReturnPath(String path, String content) throws JsxScriptException {
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
        throw new JsxScriptException(e);
      }

    } else {
      return path;
    }
  }

  private static class JsxReferenceHandler implements ReferenceHandler {

    private static Field handleField;
    private Map<Long, V8Value> refs = new HashMap<>();

    public JsxReferenceHandler() {
      if (handleField == null) {
        try {
          handleField = V8Value.class.getDeclaredField("objectHandle");
        } catch (NoSuchFieldException e) {
          throw new RuntimeException(e);
        }
        handleField.setAccessible(true);
      }
    }

    @Override
    public void v8HandleCreated(V8Value object) {
      try {
        refs.put(handleField.getLong(object), object);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void v8HandleDisposed(V8Value object) {
      try {
        refs.remove(handleField.getLong(object));
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    public void releaseAll() {
      //Copy set to avoid ConcurrentModification
      List<V8Value> values = new ArrayList<>(refs.values());
      for (V8Value object : values) {
        if (!object.isReleased()) {
          object.release();
        }
      }
      refs.clear();
      v8Runtime.removeReferenceHandler(this);
    }
  }
}
