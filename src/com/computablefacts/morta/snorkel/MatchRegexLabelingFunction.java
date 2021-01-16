package com.computablefacts.morta.snorkel;

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

@CheckReturnValue
final public class MatchRegexLabelingFunction extends AbstractLabelingFunction<String> {

  private Pattern pattern_;

  public MatchRegexLabelingFunction(String pattern) {
    super(pattern);
  }

  @Override
  public Integer apply(String text) {
    Matcher matcher = pattern().matcher(text);
    return matcher.find() ? OK : ABSTAIN;
  }

  public Pattern pattern() {
    if (pattern_ == null) {
      pattern_ =
          Pattern.compile(name(), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    }
    return pattern_;
  }
}
