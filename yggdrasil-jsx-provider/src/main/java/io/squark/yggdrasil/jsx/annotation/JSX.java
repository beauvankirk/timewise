package io.squark.yggdrasil.jsx.annotation;

import javax.enterprise.context.ConversationScoped;
import javax.enterprise.inject.Stereotype;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-02.
 * Copyright 2016
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Stereotype
@ConversationScoped
public @interface JSX {
    String value();
    boolean session() default true;
}
