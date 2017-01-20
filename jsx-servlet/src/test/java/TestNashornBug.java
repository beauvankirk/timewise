import io.squark.yggdrasil.jsx.handler.Unwrap;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import javax.script.Bindings;
import java.io.File;
import java.io.FileReader;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2017-01-20.
 * Copyright 2017
 */
public class TestNashornBug {

  @Test
  public void testBug() throws Exception {
    String polyfill = IOUtils.toString(new FileReader(new File("src/main/resources/META-INF/js-server/polyfill.js")));
    String script = polyfill +
                    "var target = {};" +
                    "" +
                    "for (propName in source) {" +
                    "   if (source.hasOwnProperty(propName)) {" +
                    "     target[propName] = source[propName];" +
                    "   }" +
                    "}" +
                    "print(target.aProperty);";

    NashornScriptEngine scriptEngine = (NashornScriptEngine) new NashornScriptEngineFactory().getScriptEngine();
    Bindings source = scriptEngine.createBindings();
    source.put("aProperty", "test123");
    ScriptObjectMirror.wrap(source, null);

    scriptEngine.put("unwrap", new Unwrap());
    scriptEngine.put("source", source);
    scriptEngine.eval(script);
  }

}
