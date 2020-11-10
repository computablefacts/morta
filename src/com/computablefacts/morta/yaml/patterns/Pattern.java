package com.computablefacts.morta.yaml.patterns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.logfmt.LogFormatter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.CheckReturnValue;

/**
 * A single pattern with its test cases.
 * 
 * <pre>
 * - name: SINGLE_WORD
 *   pattern: \p{L}+
 *   is_case_insensitive: true
 *   should_match:
 *     - word
 *     - WORD
 *     - Word
 *   should_not_match:
 *     - two words
 *     - word-with-hyphen
 * </pre>
 */
@CheckReturnValue
final public class Pattern {

  private static final Logger logger_ = LoggerFactory.getLogger(Pattern.class);

  @JsonProperty("name")
  String name_;

  @JsonProperty("description")
  String description_;

  @JsonProperty("pattern")
  String pattern_;

  @JsonProperty("is_case_insensitive")
  Boolean isCaseInsensitive_;

  @JsonProperty("should_match")
  String[] shouldMatch_;

  @JsonProperty("should_not_match")
  String[] shouldNotMatch_;

  public Pattern() {}

  public boolean isValid() {

    com.google.re2j.Pattern pattern = compile();

    for (int i = 0; shouldMatch_ != null && i < shouldMatch_.length; i++) {
      if (!pattern.matches(shouldMatch_[i])) {
        logger_.error(LogFormatter.create()
            .message("Pattern \"" + name_ + "\" should match \"" + shouldMatch_[i] + "\"")
            .formatError());
        return false;
      }
    }

    for (int i = 0; shouldNotMatch_ != null && i < shouldNotMatch_.length; i++) {
      if (pattern.matches(shouldNotMatch_[i])) {
        logger_.error(LogFormatter.create()
            .message("Pattern \"" + name_ + "\" should not match \"" + shouldNotMatch_[i] + "\"")
            .formatError());
        return false;
      }
    }
    return true;
  }

  private com.google.re2j.Pattern compile() {
    if (isCaseInsensitive_ == null || isCaseInsensitive_) {
      return com.google.re2j.Pattern.compile("^" + pattern_ + "$",
          com.google.re2j.Pattern.CASE_INSENSITIVE);
    }
    return com.google.re2j.Pattern.compile("^" + pattern_ + "$");
  }
}
