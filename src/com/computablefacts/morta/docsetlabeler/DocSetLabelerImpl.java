package com.computablefacts.morta.docsetlabeler;

import java.util.ArrayList;
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
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
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

  private final Multiset<String> ngramsCorpus_ = HashMultiset.create();
  private final Multiset<String> ngramsSubsetOk_ = HashMultiset.create();
  private final Multiset<String> ngramsSubsetKo_ = HashMultiset.create();

  private final AtomicInteger countCorpus_ = new AtomicInteger(0);
  private final AtomicInteger countSubsetOk_ = new AtomicInteger(0);
  private final AtomicInteger countSubsetKo_ = new AtomicInteger(0);

  public DocSetLabelerImpl(Languages.eLanguage language, int maxGroupSize) {

    Preconditions.checkNotNull(language, "language should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    language_ = language;
    maxGroupSize_ = maxGroupSize;
  }

  @Override
  protected void init(@NotNull List<String> corpus, @NotNull List<String> subsetOk,
      @NotNull List<String> subsetKo) {

    if (ngramsCorpus_.isEmpty()) {

      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

      corpus.stream().peek(t -> bar.update(countCorpus_.incrementAndGet(), corpus.size(),
          "Loading the whole corpus...")).forEach(text -> {
            Multiset<String> multiset = Helpers.ngrams(language_, maxGroupSize_, text);
            ngramsCorpus_.addAll(multiset);
          });

      countCorpus_.set(ngramsCorpus_.size());
      ngramsCorpus_.entrySet().removeIf(ngram -> ngram.getCount() == 1);

      System.out.println(); // Cosmetic
    }
    if (ngramsSubsetOk_.isEmpty()) {

      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

      subsetOk.stream().peek(t -> bar.update(countSubsetOk_.incrementAndGet(), subsetOk.size(),
          "Loading \"OK\" corpus...")).forEach(text -> {
            Multiset<String> multiset = Helpers.ngrams(language_, maxGroupSize_, text);
            ngramsSubsetOk_.addAll(multiset);
          });

      countSubsetOk_.set(ngramsSubsetOk_.size());
      ngramsSubsetOk_.entrySet().removeIf(ngram -> ngram.getCount() == 1);

      System.out.println(); // Cosmetic
    }
    if (ngramsSubsetKo_.isEmpty()) {

      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

      subsetKo.stream().peek(t -> bar.update(countSubsetKo_.incrementAndGet(), subsetKo.size(),
          "Loading \"KO\" corpus...")).forEach(text -> {
            Multiset<String> multiset = Helpers.ngrams(language_, maxGroupSize_, text);
            ngramsSubsetKo_.addAll(multiset);
          });

      countSubsetKo_.set(ngramsSubsetKo_.size());
      ngramsSubsetKo_.entrySet().removeIf(ngram -> ngram.getCount() == 1);

      System.out.println(); // Cosmetic
    }
  }

  @Override
  protected void uinit() {
    ngramsSubsetOk_.clear();
    countSubsetOk_.set(0);
    ngramsSubsetKo_.clear();
    countSubsetKo_.set(0);
  }

  @Override
  protected Set<String> candidates(String text) {

    Multiset<String> ngrams = Helpers.ngrams(language_, maxGroupSize_, text);

    return Sets.intersection(ngrams.elementSet(), ngramsSubsetOk_.elementSet());
  }

  @Override
  protected double computeX(String text, String candidate) {

    // the higher, the better
    double freqOk = (double) ngramsSubsetOk_.count(candidate) / (double) countSubsetOk_.get();

    if (!Double.isFinite(freqOk) || freqOk == 0.0) {
      return 0.0000001;
    }
    return freqOk; // the higher, the better
  }

  @Override
  protected double computeY(String text, String candidate) {

    // the lower, the better
    double freqKo = (double) ngramsSubsetKo_.count(candidate) / (double) countSubsetKo_.get();

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
