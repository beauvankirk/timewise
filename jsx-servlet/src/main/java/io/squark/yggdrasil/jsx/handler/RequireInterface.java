package io.squark.yggdrasil.jsx.handler;

import javax.script.ScriptException;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2017-01-01.
 * Copyright 2017
 */
@FunctionalInterface
public interface RequireInterface {
    Object require(String module) throws ScriptException;
}
