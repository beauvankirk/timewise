package io.squark.yggdrasil.jsx.handler;

import io.squark.yggdrasil.jsx.exception.JsxHandlerException;
import io.squark.yggdrasil.jsx.exception.JsxScriptException;
import io.squark.yggdrasil.jsx.servlet.Response;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;

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
    private static volatile ScriptObjectMirror realReactDOMServer = null;
    private static volatile NashornScriptEngine scriptEngine = null;
    private static volatile JSObject objectConstructor;
    private static volatile JSObject arrayConstructor;
    private static volatile JSObject babelConfig;
    private ScriptObjectMirror wrapperReactDOMServer;

    public void initialize(@Observes @Initialized(ApplicationScoped.class) Object init) throws ScriptException {
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

    public String handleJsx(Reader reader, Response response) throws JsxHandlerException {
        try {
            try {
                initializeNashorn();

                String content = IOUtils.toString(reader);
                String transformed =
                    (String) ((ScriptObjectMirror) scriptEngine.invokeMethod(babel, "transform", content, babelConfig))
                        .get("code");

                Bindings engineBindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
                //Copy map to avoid mutating original:
                Bindings transformBindings = new SimpleBindings(new HashMap<>(response.getJsxResponseContext()));
                transformBindings.put("React", engineBindings.get("React"));
                StringBuilder stringBuilder = new StringBuilder();
                ScriptObjectMirror wrapperInstance =
                    (ScriptObjectMirror) wrapperReactDOMServer.newObject(stringBuilder, realReactDOMServer);
                transformBindings.put("ReactDOMServer", wrapperInstance);
                scriptEngine.eval(transformed, transformBindings);
                return stringBuilder.toString();

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
                realReactDOMServer = (ScriptObjectMirror) scriptEngine.compile(read("META-INF/js-server/react-dom-server.js")).eval();
                wrapperReactDOMServer =
                    (ScriptObjectMirror) scriptEngine.eval(read("META-INF/js-server/ReactDOMServerWrapper.js"));
                objectConstructor = (JSObject) scriptEngine.eval("Object");
                arrayConstructor = (JSObject) scriptEngine.eval("Array");
                JSObject presets = (JSObject) arrayConstructor.newObject();
                presets.setSlot(0, "react");
                babelConfig = (JSObject) objectConstructor.newObject();
                babelConfig.setMember("presets", presets);
                logger.info("Script engine initialized.");
            }
        }
    }

}
