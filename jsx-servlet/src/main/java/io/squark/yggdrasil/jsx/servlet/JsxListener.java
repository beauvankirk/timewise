package io.squark.yggdrasil.jsx.servlet;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebListener;
import java.util.EnumSet;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-09.
 * Copyright 2016
 */
@WebListener
public class JsxListener implements ServletContextListener {

    /**
     * The constant SERVLET_NAME_PARAMETER.
     */
    public static final String SERVLET_NAME_PARAMETER = "servletName";
    /**
     * The constant DEFAULT_SERVLET_NAME.
     */
    public static final String DEFAULT_SERVLET_NAME = JsxServlet.class.getSimpleName();
    private static final String FILTER_NAME_PARAMETER = "filterName";
    private static final String DEFAULT_FILTER_NAME = JsxFilter.class.getSimpleName();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        String servletName = sce.getServletContext().getInitParameter(SERVLET_NAME_PARAMETER);
        if (servletName == null) {
            servletName = DEFAULT_SERVLET_NAME;
        }
        ServletRegistration servletRegistration = sce.getServletContext().getServletRegistration(servletName);
        if (servletRegistration == null) {
            sce.getServletContext().addServlet(servletName, JsxServlet.class).addMapping("/");
        }
        String filterName = sce.getServletContext().getInitParameter(FILTER_NAME_PARAMETER);
        if (filterName == null) {
            filterName = DEFAULT_FILTER_NAME;
        }
        FilterRegistration filterRegistration = sce.getServletContext().getFilterRegistration(filterName);
        if (filterRegistration == null) {
            sce.getServletContext().addFilter(filterName, JsxFilter.class).addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/");
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }
}
