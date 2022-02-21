package com.computablefacts.morta.patterns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.asterix.WildcardMatcher;
import com.computablefacts.logfmt.LogFormatter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.CheckReturnValue;

/**
 * A single pattern with its test cases.
 * 
 * <pre>
 * - name: SINGLE_WORD
 *   pattern: \p{L}+
 *   is_case_sensitive: true
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
  public String name_;

  @JsonProperty("description")
  public String description_;

  @JsonProperty("pattern")
  public String pattern_;

  @JsonProperty("is_case_sensitive")
  public Boolean isCaseSensitive_;

  @JsonProperty("is_wildcard")
  public Boolean isWildcard_;

  @JsonProperty("should_match")
  public String[] shouldMatch_;

  @JsonProperty("should_not_match")
  public String[] shouldNotMatch_;

  public Pattern() {}

  public boolean isValid() {

    if (isWildcard_ == null || !isWildcard_) {

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
    } else {

      String pattern = WildcardMatcher.compact(pattern_);

      for (int i = 0; shouldMatch_ != null && i < shouldMatch_.length; i++) {
        if (!WildcardMatcher.match(shouldMatch_[i], pattern)) {
          logger_.error(LogFormatter.create()
              .message("Pattern \"" + name_ + "\" should match \"" + shouldMatch_[i] + "\"")
              .formatError());
          return false;
        }
      }

      for (int i = 0; shouldNotMatch_ != null && i < shouldNotMatch_.length; i++) {
        if (WildcardMatcher.match(shouldNotMatch_[i], pattern)) {
          logger_.error(LogFormatter.create()
              .message("Pattern \"" + name_ + "\" should not match \"" + shouldNotMatch_[i] + "\"")
              .formatError());
          return false;
        }
      }
    }
    return true;
  }

  private com.google.re2j.Pattern compile() {
    if (isCaseSensitive_ == null || !isCaseSensitive_) {
      return com.google.re2j.Pattern.compile("^" + pattern_ + "$",
          com.google.re2j.Pattern.CASE_INSENSITIVE);
    }
    return com.google.re2j.Pattern.compile("^" + pattern_ + "$");
  }
}
