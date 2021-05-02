package com.computablefacts.morta.snorkel.labelingfunctions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

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
  private Function<String, List<String>> sentenceSplitter_;
  private Function<String, List<String>> wordSplitter_;

  public MatchGuesstimatedLabelingFunction(Languages.eLanguage language, int maxGroupSize,
      String pattern) {

    super(pattern);

    Preconditions.checkNotNull(language, "language should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    language_ = language;
    maxGroupSize_ = maxGroupSize;
  }

  @Override
  public Integer apply(String text) {

    Multiset<String> multiset =
        Helpers.ngrams(language_, sentenceSplitter(), wordSplitter(), maxGroupSize_, text);

    return multiset.contains(name()) ? OK : ABSTAIN;
  }

  @Override
  public Set<String> matches(String text) {
    return new HashSet<>(Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(name()));
  }

  private Function<String, List<String>> sentenceSplitter() {
    if (sentenceSplitter_ == null) {
      sentenceSplitter_ = Helpers.sentenceSplitter();
    }
    return sentenceSplitter_;
  }

  private Function<String, List<String>> wordSplitter() {
    if (wordSplitter_ == null) {
      wordSplitter_ = Helpers.wordSplitter(language_);
    }
    return wordSplitter_;
  }
}
