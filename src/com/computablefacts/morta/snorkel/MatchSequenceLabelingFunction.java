package com.computablefacts.morta.snorkel;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class MatchSequenceLabelingFunction extends AbstractLabelingFunction<String> {

  private final Languages.eLanguage language_;
  private Function<String, List<String>> sentenceSplitter_;
  private Function<String, List<String>> wordSplitter_;

  public MatchSequenceLabelingFunction(Languages.eLanguage language, String pattern) {

    super(pattern);

    Preconditions.checkNotNull(language, "language should not be null");

    language_ = language;
  }

  @Override
  public Integer apply(String text) {

    // Tokenize text
    List<String> sentences =
        sentenceSplitter().apply(text).stream().map(sentence -> wordSplitter().apply(sentence))
            .map(words -> Joiner.on(' ').join(words)).collect(Collectors.toList());

    // Find matching patterns
    for (String sentence : sentences) {
      if (sentence.contains(name())) {
        return OK;
      }
    }
    return ABSTAIN;
  }

  public Function<String, List<String>> sentenceSplitter() {
    if (sentenceSplitter_ == null) {
      sentenceSplitter_ = Helpers.sentenceSplitter();
    }
    return sentenceSplitter_;
  }

  public Function<String, List<String>> wordSplitter() {
    if (wordSplitter_ == null) {
      wordSplitter_ = Helpers.wordSplitter(language_);
    }
    return wordSplitter_;
  }
}
