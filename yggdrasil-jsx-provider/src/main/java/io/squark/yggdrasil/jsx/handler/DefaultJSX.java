package io.squark.yggdrasil.jsx.handler;

import io.squark.yggdrasil.jsx.annotation.JSX;
import io.squark.yggdrasil.jsx.exception.JsxPathException;
import io.squark.yggdrasil.jsx.servlet.JsxRequestContext;
import io.squark.yggdrasil.jsx.servlet.JsxResponseContext;
import io.squark.yggdrasil.jsx.servlet.Response;

import java.io.Serializable;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-03.
 * Copyright 2016
 */
@JSX("/")
public class DefaultJSX implements Serializable {

    @JSX("/")
    public Response defaultJSX(JsxRequestContext jsxRequestContext) throws JsxPathException {
        JsxResponseContext jsxResponseContext = new JsxResponseContext();
        //TODO: Remove
        jsxResponseContext.put("test", "wohoo");
        return new Response.Builder(Response.ResponseType.AUTO).withContext(jsxResponseContext).build();
    }

}
