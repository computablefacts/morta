package com.computablefacts.morta.textcat;

import java.util.ArrayList;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class TextCategorizer {

  private final ArrayList<FingerPrint> categories_ = new ArrayList<>();

  public TextCategorizer() {}

  public void add(FingerPrint fingerPrint) {
    categories_.add(fingerPrint);
  }

  public String categorize(String text) {
    return categorize(text, 1.03, 5);
  }

  public String categorize(String text, double threshold, int maxCandidates) {

    Preconditions.checkNotNull(text, "text should not be null");
    Preconditions.checkArgument(threshold >= 1.0, "threshold should be >= 1.0");
    Preconditions.checkArgument(maxCandidates >= 1, "maxCandidates should be >= 1");

    if (text.length() < 10) {
      return "unknown";
    }

    FingerPrint fp = new FingerPrint();
    fp.create(text);
    Map<String, Integer> categories = fp.categorize(categories_);
    int minDistance = categories.values().stream().mapToInt(i -> i).min().orElse(0);
    double newThreshold = minDistance * threshold;
    int nbCandidates = categories.entrySet().stream().filter(e -> e.getValue() <= newThreshold)
        .mapToInt(e -> 1).sum();

    return nbCandidates > maxCandidates ? "unknown" : fp.category();
  }
}
