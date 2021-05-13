package com.computablefacts.morta.snorkel.labelingfunctions;

import java.util.HashSet;
import java.util.Set;

import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Multiset;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class MatchGuesstimatedLabelingFunction extends AbstractLabelingFunction<String> {

  private final Languages.eLanguage language_;
  private final int maxGroupSize_;
  private final double weight_;

  public MatchGuesstimatedLabelingFunction(Languages.eLanguage language, int maxGroupSize,
      String pattern, double weight) {

    super(pattern);

    Preconditions.checkNotNull(language, "language should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    language_ = language;
    maxGroupSize_ = maxGroupSize;
    weight_ = weight;
  }

  @Override
  public Integer apply(String text) {

    Multiset<String> ngrams = Helpers.ngrams(language_, maxGroupSize_, text);

    return ngrams.contains(name()) ? OK : ABSTAIN;
  }

  @Override
  public Set<String> matches(String text) {
    return new HashSet<>(Splitter.on('_').trimResults().omitEmptyStrings().splitToList(name()));
  }

  @Override
  public double weight() {
    return weight_;
  }
}
