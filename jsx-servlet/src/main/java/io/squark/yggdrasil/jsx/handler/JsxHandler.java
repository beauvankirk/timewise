package io.squark.yggdrasil.jsx.handler;

import com.coveo.nashorn_modules.Require;
import com.coveo.nashorn_modules.ResourceFolder;
import io.squark.yggdrasil.jsx.annotation.JsxServletConfig;
import io.squark.yggdrasil.jsx.exception.JsxHandlerException;
import io.squark.yggdrasil.jsx.exception.JsxScriptException;
import io.squark.yggdrasil.jsx.servlet.Response;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.ScriptUtils;
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
import javax.servlet.ServletConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-12.
 * Copyright 2016
 */
@ApplicationScoped
public class JsxHandler {

    private static final Object scriptEngineLock = new Object();
    private static final Logger logger = LoggerFactory.getLogger(JsxHandler.class);
    private static volatile ScriptObjectMirror babel = null;
    private static volatile ScriptObjectMirror reactDOMServer = null;
    private static volatile NashornScriptEngine scriptEngine = null;
    private static volatile JSObject objectConstructor;
    private static volatile JSObject arrayConstructor;
    private static volatile JSObject babelConfig;

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
            }).start();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ScriptException) {
                throw (ScriptException) e.getCause();
            }
            throw e;
        }
    }

    public String handleJsx(String scriptName, Reader reader, Response response) throws JsxHandlerException {
        try {
            try {
                initializeNashorn();

                String content = IOUtils.toString(reader);
                String transformed =
                    (String) ((ScriptObjectMirror) scriptEngine.invokeMethod(babel, "transform", content, babelConfig))
                        .get("code");

                Bindings engineBindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
                //Copy map to avoid mutating original:
                logger.debug(transformed);
                //scriptEngine.eval(transformed, transformBindings);

                SimpleBindings bindings = new SimpleBindings();
                SimpleBindings result = new SimpleBindings();
                bindings.put("result", result);
                bindings.putAll(engineBindings);
                bindings.put("ReactDOMServer", reactDOMServer);
                if (response.getJsxResponseContext() != null) {
                    bindings.putAll(response.getJsxResponseContext());
                }

                scriptEngine.eval(
                    "result.exports = {}; exports = result.exports; \n\n"
                      + transformed,
                    bindings);
                Object exports = result.get("exports");

                if (exports != null) {
                    if (exports instanceof String) {
                        return (String) exports;
                    }
                    Object defaultObject = ((ScriptObjectMirror) exports).get("default");
                    if (defaultObject != null) {
                        return defaultObject.toString();
                    }
                    return exports.toString();
                }
                throw new JsxScriptException(scriptName + " did not export default of value String\n\n" + transformed);
            } catch (ScriptException | NoSuchMethodException e) {
                throw new JsxScriptException(e);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new JsxScriptException(e);
        }
    }

    private static Reader read(String path) {
        InputStream in = JsxHandler.class.getClassLoader().getResourceAsStream(path);
        return new InputStreamReader(in);
    }

    private void initializeNashorn() throws ScriptException {
        synchronized (scriptEngineLock) {
            if (scriptEngine == null) {
                logger.info("Initialising Nashorn script engine...");
                scriptEngine = (NashornScriptEngine) new NashornScriptEngineFactory().getScriptEngine();
                scriptEngine.eval(read("META-INF/js-server/polyfill.js"));
                babel = (ScriptObjectMirror) scriptEngine.eval(read("META-INF/js-server/babel.js"));
                ScriptObjectMirror react = (ScriptObjectMirror) scriptEngine.eval(read("META-INF/js-server/react.js"));
                scriptEngine.put("React", react);
                reactDOMServer = (ScriptObjectMirror) scriptEngine.compile(read("META-INF/js-server/react-dom-server.js")).eval();
                objectConstructor = (JSObject) scriptEngine.eval("Object");
                arrayConstructor = (JSObject) scriptEngine.eval("Array");
                JSObject presets = (JSObject) arrayConstructor.newObject();
                presets.setSlot(0, "react");
                presets.setSlot(1, "es2015");
                babelConfig = (JSObject) objectConstructor.newObject();
                babelConfig.setMember("presets", presets);

                ResourceFolder jsServerFolder = ResourceFolder.create(getClass().getClassLoader(), "META-INF/js-server", "UTF-8");
                Require.enable(scriptEngine, jsServerFolder);

                String resourcePath = servletConfig.getInitParameter("resource-path");
                if (resourcePath != null) {
                    if (resourcePath.endsWith("/")) {
                        resourcePath = resourcePath.substring(0, resourcePath.length() - 1);
                    }
                } else {
                    resourcePath = "META-INF/webapp";
                }

                ResourceFolder resourceFolder = ResourceFolder.create(getClass().getClassLoader(), resourcePath, "UTF-8");
                Require.enable(scriptEngine, resourceFolder);

                Require.registerHandler(new RequireJsxFileHandler(babel, babelConfig, objectConstructor, react));

                logger.info("Script engine initialized.");
            }
        }
    }

}
