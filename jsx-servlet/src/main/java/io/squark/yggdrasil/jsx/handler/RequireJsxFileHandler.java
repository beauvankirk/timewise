package io.squark.yggdrasil.jsx.handler;

import com.coveo.nashorn_modules.FileHandler;
import com.coveo.nashorn_modules.Folder;
import com.coveo.nashorn_modules.Module;
import com.coveo.nashorn_modules.Paths;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptException;
import java.util.Collections;
import java.util.List;

/**
 * Created by erik on 2016-12-28.
 */
public class RequireJsxFileHandler implements FileHandler {

  private ScriptObjectMirror babel;
  private JSObject babelConfig;
  private JSObject jsObject;
  private ScriptObjectMirror react;
  private static final Logger logger = LoggerFactory.getLogger(RequireJsxFileHandler.class);

  public RequireJsxFileHandler(
      ScriptObjectMirror babel, JSObject babelConfig, JSObject jsObject, ScriptObjectMirror react) {
    this.babel = babel;
    this.babelConfig = babelConfig;
    this.jsObject = jsObject;
    this.react = react;
  }

  @Override
  public List<String> getFileEndings() {
    return Collections.singletonList(".jsx");
  }

  @Override
  public Module compile(Folder parent, String fullPath, String code, Module parentModule)
      throws ScriptException {
    Bindings module = parentModule.getEngine().createBindings();
    Bindings exports = (Bindings) jsObject.newObject();
    Module created =
        new Module(
            parentModule.getEngine(),
            parent,
            parentModule.getCache(),
            fullPath,
            module,
            exports,
            parentModule,
            parentModule.getMainModule());

    String[] split = Paths.splitPath(fullPath);
    String filename = split[split.length - 1];
    String dirname = fullPath.substring(0, Math.max(fullPath.length() - filename.length() - 1, 0));

    String transformed;
    try {
      transformed =
          (String)
              ((ScriptObjectMirror)
                      parentModule.getEngine().invokeMethod(babel, "transform", code, babelConfig))
                  .get("code");
    } catch (NoSuchMethodException e) {
      throw new ScriptException(e);
    }

    logger.debug(transformed);

    // This mimics how Node wraps module in a function. I used to pass a 2nd parameter
    // to eval to override global context, but it caused problems Object.create.
    ScriptObjectMirror function =
        (ScriptObjectMirror)
            parentModule
                .getEngine()
                .eval(
                    "(function (exports, require, module, __filename, __dirname, React) {"
                        + transformed
                        + "})");
    function.call(
        created, created.getExports(), created, created.getModule(), filename, dirname, react);

    // Scripts are free to replace the global exports symbol with their own, so we
    // reload it from the module object after compiling the code.
    created.setExports(created.getModule().get("exports"));

    created.setLoaded();
    return created;
  }
}
