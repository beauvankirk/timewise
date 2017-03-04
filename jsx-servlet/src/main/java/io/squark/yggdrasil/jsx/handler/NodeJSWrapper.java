package io.squark.yggdrasil.jsx.handler;

import com.eclipsesource.v8.NodeJS;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;

import java.io.File;
import java.lang.reflect.Field;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2017-02-16.
 * Copyright 2017
 */
public class NodeJSWrapper {
  private NodeJS delegate;
  private V8Function require;

  protected NodeJSWrapper(NodeJS delegate) {
    this.delegate = delegate;
    try {
      Field requireField = NodeJS.class.getDeclaredField("require");
      requireField.setAccessible(true);
      require = (V8Function) requireField.get(delegate);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    V8Object process = delegate.getRuntime().getObject("process");
    process.add("platform", "java");
    process.release();
    //Todo: jack into process.stdout and process.stderr
  }

  public V8 getRuntime() {
    return delegate.getRuntime();
  }

  public boolean handleMessage() {
    return delegate.handleMessage();
  }

  public void release() {
    delegate.release();
  }

  public boolean isRunning() {
    return delegate.isRunning();
  }

  public V8Object require(File file) {
    return delegate.require(file);
  }

  public V8Object require(String request) {
    V8Array v8Array = null;
    try {
      v8Array = new V8Array(delegate.getRuntime());
      v8Array.push(request);
      return (V8Object) require.call(null, v8Array);
    } finally {
      if (v8Array != null) {
        v8Array.release();
      }
    }
  }

  public void exec(File file) {
    delegate.exec(file);
  }

  public NodeJS getDelegate() {
    return delegate;
  }
}
