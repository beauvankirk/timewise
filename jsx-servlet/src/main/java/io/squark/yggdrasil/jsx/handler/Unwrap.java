package io.squark.yggdrasil.jsx.handler;

import jdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.ScriptException;
import java.lang.reflect.Field;

/**
 * Created by erik on 2017-01-02.
 */
public class Unwrap implements UnwrapInterface {

    @Override
    public Object unwrap(Object obj) throws ScriptException {
        try {
            while (obj instanceof ScriptObjectMirror) {
                Field f = obj.getClass().getDeclaredField("sobj");
                f.setAccessible(true);
                obj = f.get(obj);
            }
            return obj;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ScriptException(e);
        }
    }
}
