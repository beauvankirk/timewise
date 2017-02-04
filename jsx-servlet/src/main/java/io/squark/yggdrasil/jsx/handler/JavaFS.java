package io.squark.yggdrasil.jsx.handler;

import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import org.apache.commons.io.IOUtils;

import javax.script.ScriptException;
import javax.servlet.ServletContext;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2017-02-02.
 * Copyright 2017
 */
public class JavaFS {

  private final NashornScriptEngine nashornScriptEngine;
  private final ServletContext servletContext;
  private final String basePath;
  private JSObject objectConstructor;
  private Object trueFn;
  private Object falseFn;

  public JavaFS(NashornScriptEngine scriptEngine, ServletContext servletContext, String basePath) {
    this.nashornScriptEngine = scriptEngine;
    this.servletContext = servletContext;
    this.basePath = basePath;
  }

  public Object statSync(Object path) throws ScriptException, FileNotFoundException, MalformedURLException {
    if (path.toString().endsWith("web_modules") || path.toString().endsWith("node_modules")) {
      path = path.toString() + "/";
    }
    String pathWithBase = basePath + path.toString();
    Object result = null;

    if (servletContext.getResourcePaths(path.toString()) != null) {
      result = buildObject(true);
    } else if (!path.toString().endsWith("/") && servletContext.getResourcePaths(path.toString() + "/") != null) {
      result = buildObject(true);
    } else if (path.toString().startsWith("./") && servletContext.getResourcePaths(pathWithBase) != null) {
      result = buildObject(true);
    } else {
      URL url = servletContext.getResource(path.toString());
      if (url == null) {
        url = servletContext.getResource(path.toString() + "/");
      }
      if (url == null) {
        url = servletContext.getResource(pathWithBase);
      }
      if (url == null) {
        url = servletContext.getResource(pathWithBase + "/");
      }
      if (url != null && url.getPath().endsWith("/")) {
        result = buildObject(true);
      } else if (url != null) {
        result = buildObject(false);
      }
    }
    if (result == null) {
      throw new FileNotFoundException(pathWithBase);
    }
    return result;
  }

  public Object readFileSync(Object path) throws IOException {
    String pathWithBase = basePath + path.toString();
    Object result = null;

    InputStream inputStream = servletContext.getResourceAsStream(path.toString());
    if (inputStream == null) {
      inputStream = servletContext.getResourceAsStream(pathWithBase);
    }
    if (inputStream != null) {
      result = IOUtils.toString(inputStream, Charset.defaultCharset());
    }

    if (result == null) {
      throw new FileNotFoundException(pathWithBase);
    }

    return result;
  }

  public Object readdirSync(Object _path) throws IOException {
    String result = (String) readFileSync(_path);
    return result.split("\n");
  }

  private JSObject buildObject(boolean isDirectory) throws ScriptException {
    JSObject object = createNewObject();
    object.setMember("isFile", isDirectory ? falseFn() : trueFn());
    object.setMember("isDirectory", isDirectory ? trueFn() : falseFn());
    object.setMember("isBlockDevice", falseFn());
    object.setMember("isCharacterDevice", falseFn());
    object.setMember("isSymbolicLink", falseFn());
    object.setMember("isFIFO", falseFn());
    object.setMember("isSocket", falseFn());
    return object;
  }

  private JSObject createNewObject() throws ScriptException {
    if (objectConstructor == null) {
      objectConstructor = (JSObject) nashornScriptEngine.eval("Object");
    }
    return (JSObject) objectConstructor.newObject();
  }

  private Object trueFn() throws ScriptException {
    if (trueFn == null) {
      trueFn = nashornScriptEngine.eval("function() { return true; }");
    }
    return trueFn;
  }

  private Object falseFn() throws ScriptException {
    if (falseFn == null) {
      falseFn = nashornScriptEngine.eval("function() { return false; }");
    }
    return falseFn;
  }
}
