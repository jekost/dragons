package com.mugloar.web;

import com.mugloar.error.ValidationException;
import java.util.regex.Pattern;

final class Validators {

  private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

  private Validators() {}

  /** Validate a path identifier before it reaches the upstream API. */
  static String requireId(String name, String value) {
    if (value == null || value.isEmpty()) {
      throw new ValidationException("Missing \"" + name + "\".");
    }
    if (!ID.matcher(value).matches()) {
      throw new ValidationException("Invalid \"" + name + "\": must be 1-64 letters, digits, \"-\" or \"_\".");
    }
    return value;
  }
}
