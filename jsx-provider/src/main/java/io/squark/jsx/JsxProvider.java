package io.squark.jsx;

import io.squark.yggdrasil.core.api.FrameworkProvider;
import io.squark.yggdrasil.core.api.exception.YggdrasilException;
import io.squark.yggdrasil.core.api.model.YggdrasilConfiguration;
import io.squark.yggdrasil.jsx.servlet.JsxServlet;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceChangeListener;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import org.jboss.weld.environment.servlet.WeldServletLifecycle;
import org.jetbrains.annotations.Nullable;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
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

        FileResourceManager fileResourceManager = new FileResourceManager(new File("timewise-business/src/main/resources/META-INF/webapp"), 8092);
        ClassPathResourceManager classPathResourceManager = new ClassPathResourceManager(this.getClass().getClassLoader(), "META-INF/webapp");
        ResourceManager combinedResourceManager = new CombinedResourceManager(fileResourceManager, classPathResourceManager);

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
                    .addMapping("/jsx/*").setLoadOnStartup(1).setRequireWelcomeFileMapping(true))
            .addWelcomePage("jsx/index.jsx").setResourceManager(combinedResourceManager)
            .setEagerFilterInit(true);

        List<String> mappings = new ArrayList<>();
        mappings.add("/jsx/");


        DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);

        manager.deploy();

        try {
            HttpHandler servletHandler = manager.start();
            ResourceHandler resourceHandler = Handlers.resource(combinedResourceManager).addWelcomeFiles("index.html");

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

    private static class CombinedResourceManager implements ResourceManager {

        private final FileResourceManager fileResourceManager;
        private final ClassPathResourceManager classPathResourceManager;

        public CombinedResourceManager(FileResourceManager fileResourceManager, ClassPathResourceManager classPathResourceManager) {
            this.fileResourceManager = fileResourceManager;
            this.classPathResourceManager = classPathResourceManager;
        }

        @Override
        public void close() throws IOException {
            fileResourceManager.close();
            classPathResourceManager.close();
        }

        @Override
        public Resource getResource(String path) throws IOException {
            Resource resource = fileResourceManager.getResource(path);
            if (resource == null) {
                resource = classPathResourceManager.getResource(path);
            }
            return resource;
        }

        @Override
        public boolean isResourceChangeListenerSupported() {
            return classPathResourceManager.isResourceChangeListenerSupported() || fileResourceManager.isResourceChangeListenerSupported();
        }

        @Override
        public void registerResourceChangeListener(ResourceChangeListener listener) {
            if (classPathResourceManager.isResourceChangeListenerSupported()) {
                classPathResourceManager.registerResourceChangeListener(listener);
            }
            if (fileResourceManager.isResourceChangeListenerSupported()) {
                fileResourceManager.registerResourceChangeListener(listener);
            }
        }

        @Override
        public void removeResourceChangeListener(ResourceChangeListener listener) {
            classPathResourceManager.removeResourceChangeListener(listener);
            fileResourceManager.removeResourceChangeListener(listener);
        }
    }
}
