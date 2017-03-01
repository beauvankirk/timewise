package io.squark.yggdrasil.jsx.exception;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-11-26.
 * Copyright 2016
 */
public class JsxHandlerException extends Exception {
  public JsxHandlerException(Throwable e) {
    super(e);
  }

  public JsxHandlerException(String message) {
    super(message);
  }

  public JsxHandlerException(String message, Throwable e) {
    super(message, e);
  }
}
