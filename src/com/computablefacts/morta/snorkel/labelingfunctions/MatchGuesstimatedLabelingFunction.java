package com.computablefacts.morta.snorkel.labelingfunctions;

import java.util.HashSet;
import java.util.Set;

import com.computablefacts.morta.snorkel.ILabelingFunction;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

@CheckReturnValue
final public class MatchGuesstimatedLabelingFunction extends AbstractLabelingFunction<String> {

  private final double weight_;
  private Pattern pattern_;

  public MatchGuesstimatedLabelingFunction(String pattern, double weight) {

    super(pattern);

    weight_ = weight;
  }

  @Override
  public Integer apply(String text) {
    Matcher matcher = pattern().matcher(text);
    return matcher.find() ? ILabelingFunction.OK : ILabelingFunction.ABSTAIN;
  }

  @Override
  public Set<String> matches(String text) {

    Set<String> set = new HashSet<>();

    if (!Strings.isNullOrEmpty(text)) {

      Matcher matcher = pattern().matcher(text);

      while (matcher.find()) {

        int start = matcher.start();
        int end = matcher.end();

        set.add(text.substring(start, end));
      }
    }
    return set;
  }

  @Override
  public double weight() {
    return weight_;
  }

  private Pattern pattern() {
    if (pattern_ == null) {
      pattern_ = Pattern.compile("(" + name() + ")", Pattern.MULTILINE | Pattern.DOTALL);
    }
    return pattern_;
  }
}
