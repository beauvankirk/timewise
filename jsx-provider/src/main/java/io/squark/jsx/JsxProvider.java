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
import java.util.Iterator;
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
        ClassPathResourceManager serverResourceManager = new ClassPathResourceManager(this.getClass().getClassLoader(), "META-INF/js-server");
        ResourceManager combinedResourceManager = new CombinedResourceManager(fileResourceManager, classPathResourceManager, serverResourceManager);

        DeploymentInfo servletBuilder = Servlets.deployment()
            .setClassLoader(JsxProvider.class.getClassLoader())
            .setContextPath("/")
            .setDeploymentName("test.war")
            .addInitParameter(WeldServletLifecycle.class.getPackage().getName() + ".archive.isolation", "false")
            .addListener(Servlets.listener(org.jboss.weld.environment.servlet.Listener.class))
            .addServlets(
                Servlets.servlet("JsxServlet", JsxServlet.class)
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

        private List<ResourceManager> resourceManagers = new ArrayList<>();

        public CombinedResourceManager(ResourceManager... managers) {
            for (ResourceManager manager : managers) {
                resourceManagers.add(manager);
            }
        }

        @Override
        public void close() throws IOException {
            for (ResourceManager manager : resourceManagers) {
                manager.close();
            }
        }

        @Override
        public Resource getResource(String path) throws IOException {
            Resource resource = null;
            Iterator<ResourceManager> resourceManagerIterator = resourceManagers.iterator();
            while (resource == null && resourceManagerIterator.hasNext()) {
                resource = resourceManagerIterator.next().getResource(path);
            }
            return resource;
        }

        @Override
        public boolean isResourceChangeListenerSupported() {
            for (ResourceManager resourceManager : resourceManagers) {
                if (resourceManager.isResourceChangeListenerSupported()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void registerResourceChangeListener(ResourceChangeListener listener) {
            for (ResourceManager resourceManager : resourceManagers) {
                if (resourceManager.isResourceChangeListenerSupported()) {
                    resourceManager.registerResourceChangeListener(listener);
                }
            }
        }

        @Override
        public void removeResourceChangeListener(ResourceChangeListener listener) {
            for (ResourceManager resourceManager : resourceManagers) {
                resourceManager.removeResourceChangeListener(listener);
            }
        }
    }
}
