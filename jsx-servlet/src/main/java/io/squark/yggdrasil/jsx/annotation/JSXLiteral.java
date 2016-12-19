package io.squark.yggdrasil.jsx.annotation;

import javax.enterprise.util.AnnotationLiteral;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-11.
 * Copyright 2016
 */
public class JSXLiteral extends AnnotationLiteral<JSX> implements JSX {

    private String path;

    public JSXLiteral(String path) {
        this.path = path;
    }

    @Override
    public String value() {
        return path;
    }

    @Override
    public boolean session() {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    public class PathParameter {

        private String path;

        public PathParameter(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }
}
