package com.computablefacts.morta.poc;

import com.computablefacts.morta.snorkel.AbstractLabelingFunction;
import com.computablefacts.nona.helpers.WildcardMatcher;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class MatchWildcardLabelingFunction extends AbstractLabelingFunction<String> {

  private String pattern_;

  public MatchWildcardLabelingFunction(String pattern) {
    super(pattern);
  }

  @Override
  public Integer apply(String text) {
    return WildcardMatcher.match(text, pattern()) ? MedianLabelModel.LABEL_OK
        : MedianLabelModel.LABEL_KO;
  }

  public String pattern() {
    if (pattern_ == null) {
      pattern_ = WildcardMatcher.compact(name());
    }
    return pattern_;
  }
}
