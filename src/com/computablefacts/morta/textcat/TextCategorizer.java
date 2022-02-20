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

    Preconditions.checkNotNull(text, "text should not be null");

    if (text.length() < 10) {
      return "unknown";
    }

    FingerPrint fp = new FingerPrint();
    fp.create(text);
    fp.categorize(categories_);
    return fp.category();
  }

  public String categorize(String text, int limit) {

    Preconditions.checkNotNull(text, "text should not be null");
    Preconditions.checkArgument(limit > 0, "limit should be > 0");

    return limit > text.length() - 1 ? categorize(text) : categorize(text.substring(0, limit));
  }

  public Map<String, Integer> categoryDistances(String text) {

    Preconditions.checkNotNull(text, "text should not be null");
    Preconditions.checkState(!categories_.isEmpty());

    FingerPrint fp = new FingerPrint();
    fp.create(text);
    return fp.categorize(categories_);
  }
}
