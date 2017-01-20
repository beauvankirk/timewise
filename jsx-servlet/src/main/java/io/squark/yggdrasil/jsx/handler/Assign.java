package io.squark.yggdrasil.jsx.handler;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.runtime.ScriptObject;

import javax.script.ScriptException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by erik on 2017-01-02.
 */
public class Assign implements AssignInterface {

    @Override
    public Object assign(Object target, Object... arguments) throws ScriptException {
        if (target == null) {
            throw new ScriptException("Target cannot be null");
        }
        Object[] sources = Arrays.copyOfRange(arguments, 1, arguments.length);
        for (Object source : sources) {
            assignSingle(target, source);
        }
        return target;
    }

    private void assignSingle(Object target, Object source) throws ScriptException {
        Map<Object, Object> properties = getProperties(source);
        if (target instanceof ScriptObject) {
            ((ScriptObject) target).putAll(properties, true);
        } else if (target instanceof ScriptObjectMirror) {
            for (Map.Entry<Object, Object> property : properties.entrySet()) {
                ((ScriptObjectMirror) target).put(property.getKey().toString(), property.getValue());
            }
        } else {
            throw new ScriptException("target is not instance of ScriptObject or ScriptObjectMirror. Is " + target.getClass());
        }
    }

    private Map<Object, Object> getProperties(Object source) throws ScriptException {
        Map<Object, Object> properties = new HashMap<>();
        if (source instanceof ScriptObject) {
            for (Map.Entry<Object, Object> entry : ((ScriptObject) source).entrySet()) {
                properties.put(entry.getKey(), entry.getValue());
            }
        } else if (source instanceof ScriptObjectMirror) {
            for (Map.Entry<String, Object> entry : ((ScriptObjectMirror) source).entrySet()) {
                properties.put(entry.getKey(), entry.getValue());
            }
        } else {
            throw new ScriptException("source is not instance of ScriptObject or ScriptObjectMirror. Is " + (source != null ? source.getClass() : "null"));
        }
        return properties;
    }
}
