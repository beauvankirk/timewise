package io.squark.yggdrasil.jsx.cache;

import io.squark.yggdrasil.jsx.servlet.Response;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-12-07.
 * Copyright 2016
 */
public class CacheObject {
    private Response response;
    private String payload;

    public CacheObject(Response response, String payload) {
        this.response = response;
        this.payload = payload;
    }

    public Response getResponse() {
        return response;
    }

    public String getPayload() {
        return payload;
    }
}
