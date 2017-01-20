package io.squark.yggdrasil.jsx.handler;

import javax.script.ScriptException;

/**
 * Created by erik on 2017-01-02.
 */
@FunctionalInterface
public interface AssignInterface {
    Object assign(Object target, Object... sources) throws ScriptException;
}
