package com.computablefacts.morta.docsetlabeler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.tartarus.snowball.SnowballStemmer;

import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.DocSetLabeler;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Joiner;
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
  private final SnowballStemmer stemmer_;
  private final Set<String> stopwords_;

  private Multiset<String> ngramsCorpus_ = HashMultiset.create();
  private Multiset<String> ngramsSubsetOk_ = HashMultiset.create();
  private Multiset<String> ngramsSubsetKo_ = HashMultiset.create();

  private AtomicInteger countCorpus_ = new AtomicInteger(0);
  private AtomicInteger countSubsetOk_ = new AtomicInteger(0);
  private AtomicInteger countSubsetKo_ = new AtomicInteger(0);

  public DocSetLabelerImpl(Languages.eLanguage language) {

    Preconditions.checkNotNull(language, "language should not be null");

    language_ = language;
    stemmer_ = Languages.stemmer(language);
    stopwords_ = Languages.stopwords(language).stream().map(stopword -> {
      stemmer_.setCurrent(stopword);
      if (stemmer_.stem()) {
        return stemmer_.getCurrent();
      }
      return Helpers.normalize(stopword);
    }).collect(Collectors.toSet());
  }

  @Override
  protected void init(@NotNull List<String> corpus, @NotNull List<String> subsetOk,
      @NotNull List<String> subsetKo) {
    if (ngramsCorpus_.isEmpty()) {

      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

      corpus.stream().peek(t -> bar.update(countCorpus_.incrementAndGet(), corpus.size(),
          "Loading the whole corpus...")).forEach(text -> {
            Set<String> set = new HashSet<>();
            split(text, set);
            ngramsCorpus_.addAll(set);
          });

      System.out.println(); // Cosmetic
    }
    if (ngramsSubsetOk_.isEmpty()) {

      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

      subsetOk.stream().peek(t -> bar.update(countSubsetOk_.incrementAndGet(), subsetOk.size(),
          "Loading \"OK\" corpus...")).forEach(text -> {
            Set<String> set = new HashSet<>();
            split(text, set);
            ngramsSubsetOk_.addAll(set);
          });

      System.out.println(); // Cosmetic
    }
    if (ngramsSubsetKo_.isEmpty()) {

      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

      subsetKo.stream().peek(t -> bar.update(countSubsetKo_.incrementAndGet(), subsetKo.size(),
          "Loading \"KO\" corpus...")).forEach(text -> {
            Set<String> set = new HashSet<>();
            split(text, set);
            ngramsSubsetKo_.addAll(set);
          });

      System.out.println(); // Cosmetic
    }
  }

  @Override
  protected void uinit() {
    ngramsSubsetOk_.clear();
    ngramsSubsetKo_.clear();
  }

  @Override
  protected Set<String> candidates(String text) {

    Set<String> set = new HashSet<>();
    split(text, set);

    return Sets.intersection(set, ngramsSubsetOk_.elementSet());
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

    // Remove a candidate iif it is a substring of a higher ranked candidate
    List<Map.Entry<String, Double>> newCandidates = new ArrayList<>();

    for (int i = 0; i < candidates.size(); i++) {

      @Var
      boolean match = false;
      String cur = candidates.get(i).getKey();

      for (int k = 0; k < i; k++) {

        String prev = candidates.get(k).getKey();

        if (prev.contains(cur)) {
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

  private void split(String text, Set<String> set) {

    int maxGroupSize = 3;
    List<List<String>> sentences = Helpers.sentenceSplitter().apply(text).stream()
        .map(sentence -> Helpers.wordSplitter(language_).apply(sentence))
        .collect(Collectors.toList());

    for (int i = 0; i < sentences.size(); i++) {

      List<String> sentence = sentences.get(i);

      for (int j = 0; j < sentence.size(); j++) {
        for (int l = j + 1; l < sentence.size() && l < j + maxGroupSize; l++) {

          List<String> words = sentence.subList(j, l);

          // Discard ngrams with stopwords on boundaries
          if (stopwords_.contains(words.get(0))
              || stopwords_.contains(words.get(words.size() - 1))) {
            continue;
          }

          String ngram = Joiner.on(' ').join(words);
          set.add(ngram);
        }
      }
    }
  }
}
