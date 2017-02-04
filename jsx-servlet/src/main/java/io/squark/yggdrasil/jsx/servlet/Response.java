package io.squark.yggdrasil.jsx.servlet;

import java.io.Serializable;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-12-01.
 * Copyright 2016
 */
public class Response implements Serializable {
    private final JsxResponseContext jsxResponseContext;
    private final boolean eval;
    private final boolean shouldWebpack;
    private int status;
    private String contentType;
    private long cacheTimeInSec;

    private Response(Builder builder) {
        this.jsxResponseContext = builder.jsxResponseContext;
        this.status = builder.status;
        this.contentType = builder.contentType;
        this.cacheTimeInSec = builder.cacheTimeInSec;
        this.eval = builder.eval;
        this.shouldWebpack = builder.shouldWebpack;
    }

    public JsxResponseContext getJsxResponseContext() {
        return jsxResponseContext;
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public long getCacheTimeInSec() {
        return cacheTimeInSec;
    }

    public boolean shouldEval() {
        return eval;
    }

    public boolean shouldWebpack() {
        return shouldWebpack;
    }

    public static class Builder {
        private static final long DEFAULT_CACHE_TIME = 30;
        private JsxResponseContext jsxResponseContext;
        private int status = HttpStatus.SC_OK;
        private String contentType;
        private long cacheTimeInSec;
        private boolean eval = true;
        private boolean shouldWebpack = false;

        public Builder() {
            contentType = "text/html";
            cacheTimeInSec = DEFAULT_CACHE_TIME;

        }

        public Builder withContext(JsxResponseContext jsxResponseContext) {
            this.jsxResponseContext = jsxResponseContext;
            return this;
        }

        public Builder withStatus(int status) {
            this.status = status;
            return this;
        }

        public Builder withContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder withCacheTimeInSec(long cacheTime) {
            this.cacheTimeInSec = cacheTime;
            return this;
        }

        public Builder shouldEval(boolean eval) {
            this.eval = eval;
            return this;
        }

        public Response build() {
            return new Response(this);
        }

        public Builder shouldWebpack(boolean shouldWebpack) {
            this.shouldWebpack = shouldWebpack;
            return this;
        }
    }
}
