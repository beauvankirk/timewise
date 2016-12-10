package io.squark.yggdrasil.jsx.handler;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import io.squark.yggdrasil.jsx.exception.JsxScriptException;
import io.squark.yggdrasil.jsx.exception.JsxHandlerException;
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
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-12.
 * Copyright 2016
 */
@ApplicationScoped
public class JsxHandler {

    public static final String TEMPLATE_DEBUG_PROPERTY = "io.squark.jsx.template.debug";
    private static final Object scriptEngineLock = new Object();
    private static final Logger logger = LoggerFactory.getLogger(JsxHandler.class);
    private static final Object freemarkerLock = new Object();
    private static volatile ScriptObjectMirror babel = null;
    private static volatile ScriptObjectMirror reactDOMServer = null;
    private static volatile NashornScriptEngine scriptEngine = null;
    private static volatile JSObject objectConstructor;
    private static volatile JSObject arrayConstructor;
    private static volatile JSObject babelConfig;
    private static Configuration freemarkerConfiguration = null;

    public void initialize(@Observes @Initialized(ApplicationScoped.class) Object init) throws ScriptException {
        try {
            new Thread(() -> {
                try {
                    initializeNashorn();
                } catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            new Thread(this::initializeFreemarker).start();
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
                Bindings transformBindings = new SimpleBindings(response.getJsxResponseContext());
                transformBindings.put("React", engineBindings.get("React"));
                return (String) reactDOMServer.callMember("renderToString", scriptEngine.eval(transformed, transformBindings));

            } catch (ScriptException | NoSuchMethodException e) {
                throw new JsxScriptException(e);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new JsxScriptException(e);
        }
    }

    public String handleTemplate(String templatePath, Response response) throws JsxHandlerException {
        try {
            initializeFreemarker();

            Template template = freemarkerConfiguration.getTemplate(templatePath);
            PrintWriter printWriter = new PrintWriter(new StringWriter());
            template.process(response.getJsxResponseContext(), printWriter);
            return printWriter.toString();
        } catch (TemplateException | IOException e) {
            throw new JsxHandlerException(e);
        }
    }

    public String handleStatic(Reader reader) throws JsxHandlerException {
        try {
            try {
                return IOUtils.toString(reader);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new JsxHandlerException(e);
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
                scriptEngine.eval(read("js-server/polyfill.js"));
                babel = (ScriptObjectMirror) scriptEngine.eval(read("js-server/babel.js"));
                ScriptObjectMirror react = (ScriptObjectMirror) scriptEngine.eval(read("js-server/react.js"));
                scriptEngine.put("React", react);
                reactDOMServer = (ScriptObjectMirror) scriptEngine.compile(read("js-server/react-dom-server.js")).eval();
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

    private void initializeFreemarker() {
        synchronized (freemarkerLock) {
            if (freemarkerConfiguration == null) {
                freemarkerConfiguration = new Configuration(Configuration.VERSION_2_3_25);
                freemarkerConfiguration.setClassLoaderForTemplateLoading(this.getClass().getClassLoader(), "webapp");
                freemarkerConfiguration.setDefaultEncoding("UTF-8");
                boolean debug = Boolean.getBoolean(TEMPLATE_DEBUG_PROPERTY);
                freemarkerConfiguration.setTemplateExceptionHandler(
                    debug ? TemplateExceptionHandler.HTML_DEBUG_HANDLER : TemplateExceptionHandler.RETHROW_HANDLER);
                freemarkerConfiguration.setLogTemplateExceptions(false);
                //todo: freemarkerConfiguration.setCacheLoader(JCS)
            }
        }
    }

}
