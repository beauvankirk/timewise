package io.squark.jsx;

import io.squark.yggdrasil.core.api.FrameworkProvider;
import io.squark.yggdrasil.core.api.exception.YggdrasilException;
import io.squark.yggdrasil.core.api.model.YggdrasilConfiguration;
import io.squark.yggdrasil.jsx.servlet.JsxFilter;
import io.squark.yggdrasil.jsx.servlet.JsxListener;
import io.squark.yggdrasil.jsx.servlet.JsxServlet;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import org.jboss.weld.environment.servlet.WeldServletLifecycle;
import org.jetbrains.annotations.Nullable;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-02.
 * Copyright 2016
 */
public class JsxProvider implements FrameworkProvider {

    @Override
    public void provide(@Nullable YggdrasilConfiguration configuration) throws YggdrasilException {

        DeploymentInfo servletBuilder = Servlets.deployment()
            .setClassLoader(JsxProvider.class.getClassLoader())
            .setContextPath("/")
            .setDeploymentName("test.war")
            .addInitParameter(WeldServletLifecycle.class.getPackage().getName() + ".archive.isolation", "false")
            .addListener(Servlets.listener(org.jboss.weld.environment.servlet.Listener.class))
            .addServlets(
                Servlets.servlet("MessageServlet", JsxServlet.class)
                    .addInitParam("message", "Hello World")
                    .addInitParam("resource-path", "META-INF/webapp/jsx/")
                    .addMapping("/jsx/*").setLoadOnStartup(1))

            .setEagerFilterInit(true);

        List<String> mappings = new ArrayList<>();
        mappings.add("/jsx");


        DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);

        manager.deploy();

        try {
            HttpHandler servletHandler = manager.start();
            ResourceHandler resourceHandler = Handlers.resource(new ClassPathResourceManager(this.getClass().getClassLoader(), "META-INF/webapp"));

            HttpHandler handler = exchange -> {
                for (String mapping : mappings) {
                    if (exchange.getRequestURI().startsWith(mapping)) {
                        servletHandler.handleRequest(exchange);
                        return;
                    }
                }
                resourceHandler.handleRequest(exchange);
            };
            Undertow server = Undertow.builder().addHttpListener(8000, "localhost").setHandler(handler).build();
            server.start();
        } catch (ServletException e) {
            e.printStackTrace();
        }
    }
}
