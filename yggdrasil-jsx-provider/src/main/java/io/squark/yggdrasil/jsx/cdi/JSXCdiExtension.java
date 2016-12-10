package io.squark.yggdrasil.jsx.cdi;

import io.squark.yggdrasil.jsx.annotation.JSX;
import io.squark.yggdrasil.jsx.annotation.JSXAnnotatedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ConversationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.util.AnnotationLiteral;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-02.
 * Copyright 2016
 */
public class JSXCdiExtension implements Extension {

    private Logger logger = LoggerFactory.getLogger(JSXCdiExtension.class);
    private Map<String, Class> jsxClasses = new HashMap<>();

    public static final Annotation conversationScopedLiteral = new AnnotationLiteral<ConversationScoped>()
    {
        private static final long serialVersionUID = 1L;
    };


    public <T> void observeResources(
        @WithAnnotations({JSX.class}) @Observes ProcessAnnotatedType<T> event, BeanManager beanManager) {
        AnnotatedType<T> annotatedType = event.getAnnotatedType();

        logger.debug("Found @JSX-annotated class " + annotatedType.getJavaClass());

        jsxClasses.put(annotatedType.getAnnotation(JSX.class).value(), annotatedType.getJavaClass());

        if (!isSessionBean(annotatedType) && !isScopeDefined(annotatedType, beanManager)) {
            logger.debug(annotatedType.getJavaClass().getName() + " has no scope. Adding ConversationScoped");
            event.setAnnotatedType(new JSXAnnotatedType<>(annotatedType, conversationScopedLiteral));
        }
    }

    /**
     * Find out if a given annotated type is explicitly bound to a scope.
     *
     * @return true if and only if a given annotated type is annotated with a scope
     *         annotation or with a stereotype which (transitively) declares a
     *         scope
     *
     *  Shamelessly stolen from Resteasy
     *  https://github.com/resteasy/Resteasy/blob/dcb47eea39fea87cadea59a599373925d66ffa4c/resteasy-cdi/src/main/java/org/jboss/resteasy/cdi/ResteasyCdiExtension.java
     */
    public static boolean isScopeDefined(AnnotatedType<?> annotatedType, BeanManager manager)
    {
        for (Annotation annotation : annotatedType.getAnnotations())
        {
            if (manager.isScope(annotation.annotationType()))
            {
                return true;
            }
            if (manager.isStereotype(annotation.annotationType()))
            {
                if (isScopeDefined(annotation.annotationType(), manager))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Find out if a given class is explicitly bound to a scope.
     *
     * @param clazz
     * @param manager
     * @return <code>true</code> if a given class is annotated with a scope
     *         annotation or with a stereotype which (transitively) declares a
     *         scope
     *
     *  Shamelessly stolen from Resteasy
     *  https://github.com/resteasy/Resteasy/blob/dcb47eea39fea87cadea59a599373925d66ffa4c/resteasy-cdi/src/main/java/org/jboss/resteasy/cdi/ResteasyCdiExtension.java
     */
    private static boolean isScopeDefined(Class<?> clazz, BeanManager manager)
    {
        for (Annotation annotation : clazz.getAnnotations())
        {
            if (manager.isScope(annotation.annotationType()))
            {
                return true;
            }
            if (manager.isStereotype(annotation.annotationType()))
            {
                if (isScopeDefined(annotation.annotationType(), manager))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static final String JAVAX_EJB_STATELESS = "javax.ejb.Stateless";
    private static final String JAVAX_EJB_SINGLETON = "javax.ejb.Singleton";

    /**
     * @param annotatedType The type to check
     * @return true or false
     *
     * Shamelessly stolen from Resteasy
     * https://github.com/resteasy/Resteasy/blob/dcb47eea39fea87cadea59a599373925d66ffa4c/resteasy-cdi/src/main/java/org/jboss/resteasy/cdi/ResteasyCdiExtension.java
     */
    private boolean isSessionBean(AnnotatedType<?> annotatedType)
    {
        for (Annotation annotation : annotatedType.getAnnotations())
        {
            Class<?> annotationType = annotation.annotationType();
            if (annotationType.getName().equals(JAVAX_EJB_STATELESS) || annotationType.getName().equals(JAVAX_EJB_SINGLETON))
            {
                logger.debug(annotatedType.getJavaClass().getName() + " has annotation " + annotationType.getName() + ". Will not modify scope");
                return true;
            }
        }
        return false;
    }

    public Map<String, Class> getJsxClasses() {
        return jsxClasses;
    }
}
