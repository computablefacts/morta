package com.computablefacts.morta.docsetlabeler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.validation.constraints.NotNull;

import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.DocSetLabeler;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

/**
 * Guesstimate interesting patterns from positively/negatively annotated texts.
 */
@CheckReturnValue
final public class DocSetLabelerImpl extends DocSetLabeler {

  private final Languages.eLanguage language_;
  private final int maxGroupSize_;

  private final Map<String, Double> subsetOk_ = new HashMap<>();
  private final Map<String, Double> subsetKo_ = new HashMap<>();

  public DocSetLabelerImpl(Languages.eLanguage language, int maxGroupSize) {

    Preconditions.checkNotNull(language, "language should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    language_ = language;
    maxGroupSize_ = maxGroupSize;
  }

  @Override
  protected void init(@NotNull List<String> corpus, @NotNull List<String> subsetOk,
      @NotNull List<String> subsetKo) {

    if (subsetOk_.isEmpty()) {

      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
      AtomicInteger count = new AtomicInteger(0);

      subsetOk.stream()
          .peek(
              t -> bar.update(count.incrementAndGet(), subsetOk.size(), "Loading \"OK\" corpus..."))
          .forEach(text -> Helpers.features(language_, maxGroupSize_, text).forEach((f, w) -> {
            if (!subsetOk_.containsKey(f)) {
              subsetOk_.put(f, w);
            } else {
              subsetOk_.put(f, Math.max(subsetOk_.get(f), w));
            }
          }));

      subsetOk_.entrySet().removeIf(f -> f.getValue() < 0.01);

      System.out.println(); // Cosmetic
    }
    if (subsetKo_.isEmpty()) {

      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
      AtomicInteger count = new AtomicInteger(0);

      subsetKo.stream()
          .peek(
              t -> bar.update(count.incrementAndGet(), subsetKo.size(), "Loading \"KO\" corpus..."))
          .forEach(text -> Helpers.features(language_, maxGroupSize_, text).forEach((f, w) -> {
            if (!subsetKo_.containsKey(f)) {
              subsetKo_.put(f, w);
            } else {
              subsetKo_.put(f, Math.max(subsetKo_.get(f), w));
            }
          }));

      subsetKo_.entrySet().removeIf(f -> f.getValue() < 0.01);

      System.out.println(); // Cosmetic
    }
  }

  @Override
  protected void uinit() {
    subsetOk_.clear();
    subsetKo_.clear();
  }

  @Override
  protected Set<String> candidates(String text) {

    Map<String, Double> features = Helpers.features(language_, maxGroupSize_, text);

    return Sets.intersection(features.keySet(), subsetOk_.keySet());
  }

  @Override
  protected double computeX(String text, String candidate) {

    if (!subsetOk_.containsKey(candidate)) {
      return 0.0000001;
    }

    // the higher, the better
    double freqOk = subsetOk_.get(candidate);

    if (!Double.isFinite(freqOk) || freqOk == 0.0) {
      return 0.0000001;
    }
    return freqOk; // the higher, the better
  }

  @Override
  protected double computeY(String text, String candidate) {

    if (!subsetKo_.containsKey(candidate)) {
      return 1.0;
    }

    // the lower, the better
    double freqKo = subsetKo_.get(candidate);

    if (!Double.isFinite(freqKo) || freqKo == 0.0) {
      return 1.0;
    }
    return 1.0 - freqKo; // the higher, the better
  }

  @Override
  protected List<Map.Entry<String, Double>> filter(
      @NotNull List<Map.Entry<String, Double>> candidates) {

    // Here, candidates are ranked in decreasing weight
    // Remove a candidate iif it is a substring or it is included in a higher ranked candidate
    List<Map.Entry<String, Double>> newCandidates = new ArrayList<>();

    for (int i = 0; i < candidates.size(); i++) {

      @Var
      boolean match = false;
      String cur = candidates.get(i).getKey();

      for (int k = 0; k < i; k++) {

        String prev = candidates.get(k).getKey();

        if (prev.contains(cur) || cur.contains(prev)) {
          match = true;
          break;
        }
      }

      if (!match) {
        newCandidates.add(candidates.get(i));
      }
    }
    return newCandidates;
  }
}
