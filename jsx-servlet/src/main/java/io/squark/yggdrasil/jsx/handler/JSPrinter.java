package io.squark.yggdrasil.jsx.handler;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.ScriptUtils;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2017-01-27.
 * Copyright 2017
 */
public class JSPrinter implements Function<Object, Void> {

  private static String patternString = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";
  private static Pattern pattern = Pattern.compile(patternString);

  private PrintStream writer;

  public JSPrinter(PrintStream writer) {
    this.writer = writer;
  }

  @Override
  public Void apply(Object arg) {
    String output;
    if (arg instanceof ScriptObjectMirror && ((ScriptObjectMirror) arg).isArray()) {
      arg = ScriptUtils.convert(arg, Object[].class);
    }
    if (arg != null && arg.getClass().isArray()) {
      Object[] args = (Object[]) arg;
      if (args.length > 1) {
        if (pattern.matcher(args[0].toString()).matches()) {
          output = ScriptUtils.format(args[0].toString(), Arrays.copyOfRange(args, 1, args.length));
        } else {
          StringBuilder builder = new StringBuilder();
          for (Object o : args) {
            builder.append(o);
          }
          output = builder.toString();
        }
      } else if (args.length == 1) {
        output = args[0].toString();
      } else {
        throw new IllegalArgumentException("Must supply arguments");
      }
    } else if (arg != null) {
      output = arg.toString();
    } else {
      throw new IllegalArgumentException("Must supply arguments");
    }
    writer.println(output);
    return null;
  }
}
