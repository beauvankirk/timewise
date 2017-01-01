package io.squark.yggdrasil.jsx.handler;

import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.runtime.ECMAException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2017-01-01.
 * Copyright 2017
 */
public class Require implements RequireInterface {

    private static final Logger logger = LoggerFactory.getLogger(Require.class);

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
        //TODO: cache result
        Object result = internalRequire(module);
        if (result == null) {
            throwModuleNotFoundException(module);
        }
        return result;
    }

    private Object internalRequire(String module) throws ScriptException {
        Path path = Paths.get((basePath + "/" + module).replaceAll("//", "/"));
        String fileName = path.getFileName().toString();
        String extension = FilenameUtils.getExtension(fileName);
        InputStream inputStream = servletContext.getResourceAsStream(path.toString());
        Object result = null;
        if (inputStream != null) {
            if (extension != null) {
                switch (extension) {
                    case "js":
                        result = handleJs(inputStream);
                        break;
                    case "json":
                        result = handleJson(inputStream);
                        break;
                    case "jsx":
                        result = handleJsx(inputStream, module);
                        break;
                    default:
                        throwUnknownModuleTypeException(module);
                }
            } else {
                return handleFolder(inputStream, module);
            }
        } else if (extension != null) {
            result = internalRequire(module + ".js");
            if (result == null) {
                result = internalRequire(module + ".json");
            }
            if (result == null) {
                result = internalRequire(module + ".jsx");
            }
        }
        return result;
    }

    private Object handleFolder(InputStream inputStream, String module) {
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
            String transformed =
                (String)
                    ((ScriptObjectMirror)
                        engine.invokeMethod(babel, "transform", code, babelConfig))
                        .get("code");

            logger.debug(transformed);

            SimpleBindings bindings = new SimpleBindings();
            bindings.putAll(engine.getBindings(ScriptContext.ENGINE_SCOPE));
            SimpleBindings result = new SimpleBindings();
            bindings.put("result", result);
            bindings.put("require", this);

            // This mimics how Node wraps module in a function. I used to pass a 2nd parameter
            // to eval to override global context, but it caused problems Object.create.
            engine.eval("load(" +
                        "{script: \"" +
                //Ugly workaround to Nashorn bug regarding SimpleBindings not being a JS object
                "result.exports = {}; exports = result.exports; " + transformed.replace("\r\n", "\n").replace("\n", "\\n").replace("\"", "\\\"") +
                "\", name: \"" + module + "\"});"
                , bindings);

            return result.get("exports");
        } catch (NoSuchMethodException | IOException e) {
            throw new ScriptException(e);
        }
    }

    private Object handleJson(InputStream inputStream) {
        return null;
    }

    private Object handleJs(InputStream inputStream) {
        return null;
    }

    private void throwModuleNotFoundException(String module) throws ScriptException {
        ScriptObjectMirror ctor = (ScriptObjectMirror) engine.eval("Error");
        Bindings error = (Bindings) ctor.newObject("Module not found: " + module);
        error.put("code", "MODULE_NOT_FOUND");
        throw new ECMAException(error, null);
    }
}
