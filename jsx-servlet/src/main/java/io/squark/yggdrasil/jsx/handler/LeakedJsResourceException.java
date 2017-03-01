package io.squark.yggdrasil.jsx.handler;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2017-03-01.
 * Copyright 2017
 */
public class LeakedJsResourceException extends RuntimeException {
  private static final long serialVersionUID = 1749974246629885903L;

  public LeakedJsResourceException(String message) {
    super(message);
  }
}
