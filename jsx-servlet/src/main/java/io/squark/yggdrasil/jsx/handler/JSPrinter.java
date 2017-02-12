package io.squark.yggdrasil.jsx.handler;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.ScriptUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2017-01-27.
 * Copyright 2017
 */
public class JSPrinter {

  private static String patternString = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";
  private static Pattern pattern = Pattern.compile(patternString);

  private static final Logger logger = LoggerFactory.getLogger("JsxServlet[JavaScript]");

  public void log(Object arg, Level level) {
    String output;
    if (arg instanceof ScriptObjectMirror && ((ScriptObjectMirror) arg).isArray()) {
      arg = ScriptUtils.convert(arg, Object[].class);
    }
    if (arg instanceof Object[]) {
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
    output = StringEscapeUtils.unescapeJson(output);
    switch (level) {
      case ERROR:
        logger.error(output);
        break;
      case WARN:
        logger.warn(output);
        break;
      case INFO:
        logger.info(output);
        break;
      case DEBUG:
        logger.debug(output);
        break;
      case TRACE:
        logger.trace(output);
        break;
    }
  }
}
