package io.squark.yggdrasil.jsx.servlet;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import io.squark.yggdrasil.jsx.annotation.JSX;
import io.squark.yggdrasil.jsx.annotation.JsxServletConfig;
import io.squark.yggdrasil.jsx.cache.CacheKey;
import io.squark.yggdrasil.jsx.cache.CacheManager;
import io.squark.yggdrasil.jsx.cache.CacheObject;
import io.squark.yggdrasil.jsx.cdi.JSXCdiExtension;
import io.squark.yggdrasil.jsx.exception.JsxHandlerException;
import io.squark.yggdrasil.jsx.exception.JsxIllegalMethodException;
import io.squark.yggdrasil.jsx.exception.JsxMultipleBeansException;
import io.squark.yggdrasil.jsx.exception.JsxPathException;
import io.squark.yggdrasil.jsx.handler.JsxHandler;
import org.apache.commons.jcs.engine.ElementAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import javax.servlet.AsyncContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-08.
 * Copyright 2016
 */
public class JsxServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(JsxServlet.class);
    private static final String WITH_DELIMITER = "((?<=%1$s)|(?=%1$s))";
    private BeanManager beanManager;
    private JSXCdiExtension jsxCdiExtension;
    private JsxHandler jsxHandler;
    private ConcurrentHashMap<String, Method> cachedMatches = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Method, Object> cachedInstances = new ConcurrentHashMap<>();
    private CacheManager cacheManager;

    public static final boolean DISABLE_CACHE = Boolean.getBoolean("timewise.disableCache");


    @Inject
    public void setBeanManager(BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    @Inject
    public void setJsxCdiExtension(JSXCdiExtension jsxCdiExtension) {
        this.jsxCdiExtension = jsxCdiExtension;
    }

    @Inject
    public void setJsxHandler(JsxHandler jsxHandler) {
        this.jsxHandler = jsxHandler;
    }

    @Inject
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Inject
    private Event<ServletConfig> servletConfigEvent;

    @Produces
    @JsxServletConfig
    public ServletConfig getJsxServletConfig() {
        return getServletConfig();
    }

    @Override
    public void init() throws ServletException {
        servletConfigEvent.fire(getServletConfig());
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
        throws ServletException, IOException {

        try {
            String pathInfo = httpServletRequest.getPathInfo();
            String path = httpServletRequest.getServletPath() + (pathInfo != null ? pathInfo : "");
            URL file = getServletContext().getResource(path);

            if (file == null) {
                httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            JsxRequestContext jsxRequestContext = buildJsxContext(httpServletRequest, path);
            CacheKey cacheKey = new CacheKey(jsxRequestContext);
            CacheObject cacheObject = cacheManager.get(cacheKey);
            if (cacheObject != null) {
                doReturn(httpServletResponse, cacheObject.getResponse(), cacheObject.getPayload());
                return;
            }

            Method match = getCachedMatch(path);
            if (match == null) {
                Path fixedServletPath = Paths.get("/", Paths.get(httpServletRequest.getServletPath()).getName(0).toString());
                Path relative = fixedServletPath.relativize(Paths.get(path));
                String relativePath = "/" + relative.toString();
                Multimap < String[], Method> candidates = getJSXCandidates(relativePath);
                match = getBestMatch(candidates, relativePath);
            }
            if (match == null) {
                super.doGet(httpServletRequest, httpServletResponse);
            } else {
                cacheMatch(path, match);
                Object instance = getCachedInstance(match);
                if (instance == null) {
                    instance = getInstance(match);
                }
                if (instance == null) {
                    super.doGet(httpServletRequest, httpServletResponse);
                } else {
                    cacheInstance(match, instance);

                    validateMethod(match);
                    Response response = (Response) match.invoke(instance, jsxRequestContext);
                    String payload = jsxHandler.handleJsx(path, file, response);
                    if (response.getCacheTimeInSec() > 0 && !DISABLE_CACHE) {
                        ElementAttributes cacheAttributes = (ElementAttributes) cacheManager.getDefaultElementAttributes();
                        cacheAttributes.setCreateTime();
                        cacheAttributes.setLastAccessTimeNow();
                        cacheAttributes.setMaxLife(response.getCacheTimeInSec());
                        cacheManager.put(cacheKey, new CacheObject(response, payload), cacheAttributes);
                    }
                    doReturn(httpServletResponse, response, payload);
                    return;
                }
            }
        } catch (JsxPathException | JsxMultipleBeansException | IllegalAccessException | InvocationTargetException | JsxIllegalMethodException | JsxHandlerException e) {
            throw new ServletException(e);
        }
        super.doGet(httpServletRequest, httpServletResponse);
    }

    private void doGetAsync(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        AsyncContext asyncContext = httpServletRequest.startAsync(httpServletRequest, httpServletResponse);
        ((Queue<AsyncContext>) httpServletRequest.getServletContext().getAttribute("jsxQueue")).add(asyncContext);
        
    }

    private URL getFile(String path) throws MalformedURLException {
        URL url = this.getClass().getClassLoader().getResource(path);
        if (url == null) {
            File file = new File("timewise-business/src/main/resources/META-INF/webapp/jsx").getAbsoluteFile();
            if (file.exists()) {
                return file.toURI().toURL();
            }
        }
        return url;
    }

    @Override
    protected long getLastModified(HttpServletRequest req) {
        return super.getLastModified(req);
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doHead(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPut(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doDelete(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doOptions(req, resp);
    }

    private static void doReturn(HttpServletResponse httpServletResponse, Response response, String payload) throws IOException {
        httpServletResponse.setStatus(response.getStatus());
        httpServletResponse.setContentType(response.getContentType());

        long expiry = new Date().getTime() + response.getCacheTimeInSec() * 1000;
        httpServletResponse.setDateHeader("Expires", expiry);
        httpServletResponse.setHeader("Cache-Control", "max-age=" + response.getCacheTimeInSec());

        httpServletResponse.getWriter().write(payload);
        httpServletResponse.getWriter().close();
    }

    private void cacheInstance(Method match, Object instance) {
        cachedInstances.put(match, instance);
    }

    private Object getCachedInstance(Method match) {
        return cachedInstances.get(match);
    }

    private void cacheMatch(String pathInfo, Method match) {
        cachedMatches.put(pathInfo, match);
    }

    private Method getCachedMatch(String pathInfo) {
        return cachedMatches.get(pathInfo);
    }

    private JsxRequestContext buildJsxContext(HttpServletRequest req, String path) {
        JsxRequestContext jsxRequestContext = new JsxRequestContext();
        jsxRequestContext.setPath(path);
        jsxRequestContext.setMethod(req.getMethod());
        jsxRequestContext.setParameters(req.getParameterMap());
        return jsxRequestContext;
    }

    private void validateMethod(Method method) throws JsxIllegalMethodException {
        if (method.getReturnType() != Response.class) {
            throw new JsxIllegalMethodException("Method " + method + " must return " + Response.class);
        }
        if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != JsxRequestContext.class) {
            throw new JsxIllegalMethodException(
                "Method " + method + " must accept one and only one parameter of type " + JsxRequestContext.class);
        }
    }

    private Object getInstance(Method match) throws JsxMultipleBeansException {
        if (beanManager != null) {
            Set<Bean<?>> beans = beanManager.getBeans(match.getDeclaringClass(), new AnnotationLiteral<Any>() {});
            if (beans.size() > 1) {
                throw new JsxMultipleBeansException(
                    "Found multiple beans of type " + match.getDeclaringClass() + ". Don't know which to use.");
            } else if (beans.isEmpty()) {
                return null;
            }
            Bean<?> bean = beanManager.resolve(beans);
            if (bean == null) {
                return null;
            }
            CreationalContext<?> context = beanManager.createCreationalContext(bean);
            return bean.getBeanClass().cast(beanManager.getReference(bean, bean.getBeanClass(), context));
        } else {
            //TODO: Reflection instance
            return null;
        }
    }

    private Method getBestMatch(Multimap<String[], Method> candidates, String pathInfo) throws JsxPathException {
        if (candidates == null) {
            return null;
        }
        ListMultimap<Integer, Map.Entry<String[], Method>> weighted =
            MultimapBuilder.treeKeys(Comparator.<Integer>reverseOrder()).arrayListValues().build();

        String[] targetSegments = pathInfo.split(String.format(WITH_DELIMITER, '/'));
        for (Map.Entry<String[], Method> candidate : candidates.entries()) {
            if (Arrays.equals(targetSegments, candidate.getKey())) {
                weighted.put(100, candidate);
            } else {
                weighted.put((int) Math.round(((double) candidate.getKey().length / (double) targetSegments.length) * 100),
                    candidate);
            }
        }
        if (weighted.size() == 0) {
            throw new JsxPathException("Failed to find match for " + pathInfo);
        }
        if (weighted.size() > 1) {
            Integer firstKey = weighted.keys().iterator().next();
            List<Map.Entry<String[], Method>> firstList = weighted.get(firstKey);
            if (firstList.size() > 1) {
                throw new JsxPathException("Ambiguous matches: " + firstList);
            }
            Optional<Map.Entry<String[], Method>> first = firstList.stream().findFirst();
            if (first.isPresent()) {
                return first.get().getValue();
            } else {
                //Should not happen.
                throw new JsxPathException("Unknown error");
            }
        }
        Optional<Map.Entry<String[], Method>> first = weighted.values().stream().findFirst();
        if (first.isPresent()) {
            return first.get().getValue();
        } else {
            //Should not happen
            throw new JsxPathException("Unknown error");
        }
    }

    private Multimap<String[], Method> getJSXCandidates(String pathInfo) {
        if (jsxCdiExtension != null) {
            Multimap<String[], Method> candidates = null;
            for (Map.Entry<String, Class> entry : jsxCdiExtension.getJsxClasses().entrySet()) {
                if (pathInfo.startsWith(entry.getKey())) {
                    Multimap<String[], Method> candidatesForClass =
                        getJSXCandidatesForClass(pathInfo, entry.getKey(), entry.getValue());
                    if (candidatesForClass != null) {
                        //Only instantiate Multimap if needed
                        if (candidates == null) {
                            candidates = MultimapBuilder.hashKeys().arrayListValues().build();
                        }
                        candidates.putAll(candidatesForClass);
                    }
                }
            }
            return candidates;
        }
        //todo: lookup from servlet config
        return null;
    }

    private Multimap<String[], Method> getJSXCandidatesForClass(String pathInfo, String clazzPath, Class clazz) {
        Multimap<String[], Method> candidates = null;
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            JSX jsx = method.getAnnotation(JSX.class);
            if (jsx != null) {
                String combinedPath = clazzPath;
                if (!combinedPath.endsWith("/")) {
                    combinedPath += '/';
                }
                combinedPath += jsx.value();
                combinedPath = combinedPath.replace("//", "/");
                if (pathInfo.startsWith(combinedPath)) {
                    //Only instantiate multimap if needed
                    if (candidates == null) {
                        candidates = MultimapBuilder.hashKeys().arrayListValues().build();
                    }
                    candidates.put(combinedPath.split(String.format(WITH_DELIMITER, '/')), method);
                }
            }
        }
        return candidates;
    }
}
