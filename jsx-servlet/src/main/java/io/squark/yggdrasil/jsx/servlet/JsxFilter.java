package io.squark.yggdrasil.jsx.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * The type Jsx filter.
 */
public class JsxFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(JsxFilter.class);

    /**
     * The constant SERVLET_NAME_PARAMETER.
     */
    public static final String SERVLET_NAME = "servletName";
    /**
     * The constant DEFAULT_SERVLET_NAME.
     */
    public static final String DEFAULT_SERVLET_NAME = JsxServlet.class.getSimpleName();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (shouldHandle(request)) {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {

    }

    private boolean shouldHandle(ServletRequest request) {
        if (request instanceof HttpServletRequest) {
            logger.debug("Handling " + ((HttpServletRequest) request).getMethod() + " " + ((HttpServletRequest) request).getPathInfo());
            return true; //todo: check method and availability
        }
        return false;
    }
}
