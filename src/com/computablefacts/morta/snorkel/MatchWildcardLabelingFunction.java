package com.computablefacts.morta.snorkel;

import java.util.List;
import java.util.stream.Collectors;

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
    return WildcardMatcher.match(Helpers.normalize(text), pattern()) ? MedianLabelModel.LABEL_OK
        : ABSTAIN;
  }

  public String pattern() {
    if (pattern_ == null) {
      pattern_ = WildcardMatcher.compact(name());
    }
    return pattern_;
  }

  public List<String> literals() {
    return WildcardMatcher.split(pattern()).stream().filter(p -> !"*".equals(p) && !"?".equals(p))
        .collect(Collectors.toList());
  }
}
