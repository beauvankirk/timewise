package io.squark.jsx;

import io.squark.yggdrasil.core.api.FrameworkProvider;
import io.squark.yggdrasil.core.api.exception.YggdrasilException;
import io.squark.yggdrasil.core.api.model.YggdrasilConfiguration;
import io.squark.yggdrasil.jsx.servlet.JsxFilter;
import io.squark.yggdrasil.jsx.servlet.JsxListener;
import io.squark.yggdrasil.jsx.servlet.JsxServlet;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import org.jboss.weld.environment.servlet.WeldServletLifecycle;
import org.jetbrains.annotations.Nullable;

import javax.servlet.ServletException;

import static io.undertow.servlet.Servlets.defaultContainer;
import static io.undertow.servlet.Servlets.deployment;
import static io.undertow.servlet.Servlets.filter;
import static io.undertow.servlet.Servlets.listener;
import static io.undertow.servlet.Servlets.servlet;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-02.
 * Copyright 2016
 */
public class JsxProvider implements FrameworkProvider {

    @Override
    public void provide(@Nullable YggdrasilConfiguration configuration) throws YggdrasilException {
        DeploymentInfo servletBuilder = deployment()
            .setClassLoader(JsxProvider.class.getClassLoader())
            .setContextPath("/")
            .setDeploymentName("test.war").addListener(listener(JsxListener.class))
            .addInitParameter(WeldServletLifecycle.class.getPackage().getName() + ".archive.isolation", "false")
            .addListener(listener(org.jboss.weld.environment.servlet.Listener.class))
            .addFilter(filter(JsxFilter.class))
            .addServlets(
                servlet("MessageServlet", JsxServlet.class)
                    .addInitParam("message", "Hello World")
                    .addMapping("/*")).setEagerFilterInit(true);

        DeploymentManager manager = defaultContainer().addDeployment(servletBuilder);
        manager.deploy();

        HttpHandler servletHandler = null;
        try {
            servletHandler = manager.start();
        } catch (ServletException e) {
            e.printStackTrace();
        }
        PathHandler path = Handlers.path(Handlers.redirect("/")).addPrefixPath("/", servletHandler);
        Undertow server = Undertow.builder().addHttpListener(8080, "localhost").setHandler(path).build();
        server.start();
    }
}
