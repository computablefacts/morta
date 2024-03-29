package com.computablefacts.morta.labelingfunctions;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.computablefacts.asterix.WildcardMatcher;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class MatchWildcardLabelingFunction extends AbstractLabelingFunction<String> {

  private String pattern_;

  public MatchWildcardLabelingFunction(String pattern) {
    super(pattern);
  }

  @Override
  public Integer apply(String text) {
    return WildcardMatcher.match(text, pattern()) ? OK : ABSTAIN;
  }

  @Override
  public Set<String> matches(String text) {
    if (Strings.isNullOrEmpty(text)) {
      return new HashSet<>();
    }
    return WildcardMatcher.match(text, pattern())
        ? WildcardMatcher.split(pattern()).stream().filter(p -> !"*".equals(p) && !"?".equals(p))
            .collect(Collectors.toSet())
        : new HashSet<>();
  }

  private String pattern() {
    if (pattern_ == null) {
      pattern_ = WildcardMatcher.compact(name());
    }
    return pattern_;
  }
}
