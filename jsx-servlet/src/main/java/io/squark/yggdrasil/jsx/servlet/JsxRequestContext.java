package io.squark.yggdrasil.jsx.servlet;

import java.io.Serializable;
import java.util.Map;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-12.
 * Copyright 2016
 */
public class JsxRequestContext implements Serializable {

    private static final long serialVersionUID = -6311766779417760443L;
    private String path;
    private String method;
    private Map<String, String[]> parameters;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, String[]> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String[]> parameters) {
        this.parameters = parameters;
    }

    @Override
    public int hashCode() {
        int result = path != null ? path.hashCode() : 0;
        result = 31 * result + (method != null ? method.hashCode() : 0);
        result = 31 * result + (parameters != null ? parameters.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JsxRequestContext that = (JsxRequestContext) o;

        if (path != null ? !path.equals(that.path) : that.path != null) return false;
        if (method != null ? !method.equals(that.method) : that.method != null) return false;
        return parameters != null ? parameters.equals(that.parameters) : that.parameters == null;
    }

    @Override
    public String toString() {
        return "JsxRequestContext{" + "path='" + path + '\'' + ", method='" + method + '\'' + ", parameters=" + parameters + '}';
    }


}
