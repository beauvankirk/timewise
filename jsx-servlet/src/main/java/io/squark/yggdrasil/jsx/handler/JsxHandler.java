package io.squark.yggdrasil.jsx.handler;

import com.coveo.nashorn_modules.FilesystemFolder;
import com.coveo.nashorn_modules.Folder;
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
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import javax.enterprise.context.ApplicationScoped;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

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

    public String handleJsx(String path, String content, Response response) throws JsxHandlerException {
        try {
            try {
                initializeNashorn();

                String transformed =
                    (String) ((ScriptObjectMirror) scriptEngine.invokeMethod(babel, "transform", content, babelConfig))
                        .get("code");
                logger.debug(transformed);

                Bindings engineBindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
                SimpleBindings bindings = new SimpleBindings();
                bindings.putAll(engineBindings);
                SimpleBindings result = new SimpleBindings();
                bindings.put("result", result);
                bindings.put("ReactDOMServer", reactDOMServer);
                if (response.getJsxResponseContext() != null) {
                    bindings.putAll(response.getJsxResponseContext());
                }
                ResourceManagerFolder resourceManagerFolder = new ResourceManagerFolder(servletConfig.getServletContext(), path.substring(0, path.lastIndexOf("/")), null);
                Require.enable(scriptEngine, resourceManagerFolder);

                scriptEngine.eval(
                        //Ugly workaround to Nashorn bug regarding SimpleBindings not being a JS object
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
                throw new JsxScriptException(path + " did not export default of value String\n\n" + transformed);
            } catch (ScriptException | NoSuchMethodException e) {
                throw new JsxScriptException(e);
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

                Require.registerHandler(new RequireJsxFileHandler(babel, babelConfig, objectConstructor, react));

                logger.info("Script engine initialized.");
            }
        }
    }

    private static class ResourceManagerFolder implements Folder {

        private ServletContext servletContext;
        private String path;
        private Folder parent;

        public ResourceManagerFolder(ServletContext servletContext) {
            this.servletContext = servletContext;
            this.path = "";
        }

        public ResourceManagerFolder(ServletContext servletContext, String path, Folder parent) {
            this.servletContext = servletContext;
            this.path = path;
            this.parent = parent;
        }

        @Override
        public Folder getParent() {
            return null;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public String getFile(String name) {
            try {
                URL file = servletContext.getResource(name);
                if (file == null) {
                    return null;
                }
                return IOUtils.toString(file, Charset.defaultCharset());
            } catch (IOException e) {
                logger.error(Marker.ANY_MARKER, e);
            }
            return null;
        }

        @Override
        public Folder getFolder(String name) {
            try {
                URL folder = servletContext.getResource(path + name);
                if (folder == null) {
                    return null;
                }
                return new ResourceManagerFolder(servletContext, path + name + "/", this);
            } catch (MalformedURLException e) {
                logger.error(Marker.ANY_MARKER, e);
            }
            return null;
        }
    }

    private static class CombinedFolder implements Folder {

        List<Folder> children;

        public CombinedFolder(ResourceFolder jsServerFolder, ResourceFolder resourceFolder, FilesystemFolder filesystemFolder) {
            children = new ArrayList<>();
            children.add(jsServerFolder);
            children.add(resourceFolder);
            children.add(filesystemFolder);
        }

        @Override
        public Folder getParent() {
            return null;
        }

        @Override
        public String getPath() {
            return "";
        }

        @Override
        public String getFile(String name) {
            for (Folder child : children) {
                String file = child.getFile(name);
                if (file != null) {
                    return file;
                }
            }
            return null;
        }

        @Override
        public Folder getFolder(String name) {
            for (Folder child : children) {
                Folder folder = child.getFolder(name);
                if (folder != null) {
                    return folder;
                }
            }
            return null;
        }
    }
}
