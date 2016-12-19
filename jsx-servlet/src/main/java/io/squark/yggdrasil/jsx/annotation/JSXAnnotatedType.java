package io.squark.yggdrasil.jsx.annotation;

import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-10.
 * Copyright 2016
 */
public class JSXAnnotatedType<T> implements AnnotatedType<T> {

    private Set<Annotation> annotations = new HashSet<>();

    @Override
    public Class getJavaClass() {
        return delegate.getJavaClass();
    }

    @Override
    public Set<AnnotatedConstructor<T>> getConstructors() {
        return delegate.getConstructors();
    }

    @Override
    public Set<AnnotatedMethod<? super T>> getMethods() {
        return delegate.getMethods();
    }

    @Override
    public Set<AnnotatedField<? super T>> getFields() {
        return delegate.getFields();
    }

    @Override
    public java.lang.reflect.Type getBaseType() {
        return delegate.getBaseType();
    }

    @Override
    public Set<java.lang.reflect.Type> getTypeClosure() {
        return delegate.getTypeClosure();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotationType.isAssignableFrom(annotation.annotationType())) {
                return (T) annotation;
            }
        }
        return null;
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return Collections.unmodifiableSet(annotations);
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return delegate.isAnnotationPresent(annotationType);
    }

    private AnnotatedType delegate;

    public JSXAnnotatedType(AnnotatedType type, Annotation scopeToAdd) {
        this.delegate = type;
        this.annotations.addAll(type.getAnnotations());
        this.annotations.add(scopeToAdd);
    }
}
