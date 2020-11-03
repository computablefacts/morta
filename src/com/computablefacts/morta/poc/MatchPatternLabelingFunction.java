package com.computablefacts.morta.poc;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.computablefacts.morta.snorkel.AbstractLabelingFunction;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class MatchPatternLabelingFunction extends AbstractLabelingFunction<String> {

  private final Languages.eLanguage language_;
  private final Function<String, List<String>> sentenceSplitter_;
  private final Function<String, List<String>> wordSplitter_;

  public MatchPatternLabelingFunction(Languages.eLanguage language, String pattern) {

    super(pattern);

    Preconditions.checkNotNull(language, "language should not be null");

    language_ = language;
    sentenceSplitter_ = Helpers.sentenceSplitter();
    wordSplitter_ = Helpers.wordSplitter(language);
  }

  @Override
  public Integer apply(String text) {

    // Tokenize text
    List<String> sentences =
        sentenceSplitter_.apply(text).stream().map(sentence -> wordSplitter_.apply(sentence))
            .map(words -> Joiner.on(' ').join(words)).collect(Collectors.toList());

    // Find matching patterns
    for (String sentence : sentences) {
      if (sentence.contains(name())) {
        return MedianLabelModel.LABEL_OK;
      }
    }
    return MedianLabelModel.LABEL_KO;
  }
}
