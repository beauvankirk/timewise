package io.squark.yggdrasil.jsx.cache;

import io.squark.yggdrasil.jsx.servlet.JsxRequestContext;

import java.io.Serializable;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-12-07.
 * Copyright 2016
 */
public class CacheKey implements Serializable {
    private final JsxRequestContext jsxRequestContext;

    public CacheKey(JsxRequestContext jsxRequestContext) {
        this.jsxRequestContext = jsxRequestContext;
    }

    public JsxRequestContext getJsxRequestContext() {
        return jsxRequestContext;
    }

    @Override
    public int hashCode() {
        return jsxRequestContext != null ? jsxRequestContext.hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CacheKey cacheKey = (CacheKey) o;

        return jsxRequestContext != null ? jsxRequestContext.equals(cacheKey.jsxRequestContext) :
               cacheKey.jsxRequestContext == null;
    }

    @Override
    public String toString() {
        return "CacheKey{" + "jsxRequestContext=" + jsxRequestContext + '}';
    }


}
