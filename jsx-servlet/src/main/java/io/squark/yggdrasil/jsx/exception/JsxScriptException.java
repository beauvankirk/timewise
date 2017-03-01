package io.squark.yggdrasil.jsx.exception;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-13.
 * Copyright 2016
 */
public class JsxScriptException extends JsxHandlerException {
  public JsxScriptException(Exception e) {
    super(e);
  }

  public JsxScriptException(String message) {
    super(message);
  }

  public JsxScriptException(String message, Throwable e) {
    super(message, e);
  }
}
