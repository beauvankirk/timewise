package io.squark.yggdrasil.jsx.servlet;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-12-01.
 * Copyright 2016
 */
public class Response {
    private final ResponseType responseType;
    private final JsxResponseContext jsxResponseContext;
    private int status;
    private String contentType;
    private long cacheTimeInSec;

    private Response(Builder builder) {
        this.responseType = builder.responseType;
        this.jsxResponseContext = builder.jsxResponseContext;
        this.status = builder.status;
        this.contentType = builder.contentType;
        this.cacheTimeInSec = builder.cacheTimeInSec;
    }

    public ResponseType getResponseType() {
        return responseType;
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

    public enum ResponseType {
        JSX, TEMPLATE, STATIC, AUTO
    }

    public static class Builder {
        private ResponseType responseType;
        private JsxResponseContext jsxResponseContext;
        private int status = HttpStatus.SC_OK;
        private String contentType;
        private long cacheTimeInSec;

        private static final long DEFAULT_CACHE_TIME = 30;
        private static final long DEFAULT_CACHE_TIME_STATIC = 300;

        public Builder(ResponseType responseType) {
            this.responseType = responseType;
            //todo: handle auto
            switch (responseType) {
                case JSX:
                    contentType = "application/javascript";
                    cacheTimeInSec = DEFAULT_CACHE_TIME;
                    break;
                case TEMPLATE:
                    contentType = "application/html";
                    cacheTimeInSec = DEFAULT_CACHE_TIME;
                    break;
                default:
                    contentType = "*/*";
                    cacheTimeInSec = DEFAULT_CACHE_TIME_STATIC;
            }
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

        public Response build() {
            return new Response(this);
        }
    }
}
