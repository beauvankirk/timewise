package io.squark.yggdrasil.jsx.handler;

import javax.script.ScriptException;

/**
 * Created by erik on 2017-01-02.
 */
@FunctionalInterface
public interface UnwrapInterface {
    Object unwrap(Object obj) throws ScriptException;
}
