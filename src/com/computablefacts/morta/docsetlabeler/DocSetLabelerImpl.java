package com.computablefacts.morta.docsetlabeler;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import com.computablefacts.morta.snorkel.Helpers;
import org.tartarus.snowball.SnowballStemmer;

import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.BagOfTexts;
import com.computablefacts.nona.helpers.IBagOfTexts;
import com.computablefacts.nona.helpers.Languages;
import com.computablefacts.nona.helpers.Strings;
import com.computablefacts.nona.helpers.Text;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
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

  private IBagOfTexts bagCorpus_ = null;
  private IBagOfTexts bagSubsetOk_ = null;
  private IBagOfTexts bagSubsetKo_ = null;

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
    if (bagCorpus_ == null) {

      AtomicInteger count = new AtomicInteger(0);
      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
      BagOfTexts bag = new BagOfTexts(Helpers.sentenceSplitter(), Helpers.wordSplitter(language_));

      corpus.stream().peek(
          t -> bar.update(count.incrementAndGet(), corpus.size(), "Loading the whole corpus..."))
          .forEach(bag::add);

      bagCorpus_ = new com.computablefacts.morta.docsetlabeler.BagOfTexts(bag.freezeBagOfTexts());

      System.out.println(); // Cosmetic
    }
    if (bagSubsetOk_ == null) {

      AtomicInteger count = new AtomicInteger(0);
      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
      BagOfTexts bag = new BagOfTexts(Helpers.sentenceSplitter(), Helpers.wordSplitter(language_));

      subsetOk.stream()
          .peek(
              t -> bar.update(count.incrementAndGet(), subsetOk.size(), "Loading \"OK\" corpus..."))
          .forEach(bag::add);

      bagSubsetOk_ = new com.computablefacts.morta.docsetlabeler.BagOfTexts(bag.freezeBagOfTexts());

      System.out.println(); // Cosmetic
    }
    if (bagSubsetKo_ == null) {

      AtomicInteger count = new AtomicInteger(0);
      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
      BagOfTexts bag = new BagOfTexts(Helpers.sentenceSplitter(), Helpers.wordSplitter(language_));

      subsetKo.stream()
          .peek(
              t -> bar.update(count.incrementAndGet(), subsetKo.size(), "Loading \"KO\" corpus..."))
          .forEach(bag::add);

      bagSubsetKo_ = new com.computablefacts.morta.docsetlabeler.BagOfTexts(bag.freezeBagOfTexts());

      System.out.println(); // Cosmetic
    }
  }

  @Override
  protected void uinit() {
    bagSubsetOk_ = null;
    bagSubsetKo_ = null;
  }

  @Override
  protected Set<String> candidates(String text) {
    return Sets
        .union(corpusTextUnigrams(text),
            Sets.union(corpusTextBigrams(text), corpusTextTrigrams(text)))
        .stream().filter(ngram -> {

          // Discard ngrams with very small words on boundaries
          return ngram.get(0).length() >= 3 && ngram.get(ngram.size() - 1).length() >= 3;
        }).filter(ngram -> {

          // Discard ngrams with stopwords on boundaries
          return !stopwords_.contains(ngram.get(0))
              && !stopwords_.contains(ngram.get(ngram.size() - 1));
        }).filter(ngram -> {

          // Discard ngrams that contain numbers
          return ngram.stream().noneMatch(Strings::isNumber);
        }).filter(ngram -> {

          // Discard ngrams that do not belong to the OK subset
          if (ngram.size() == 1) {
            return bagSubsetOk_.bagOfWords().count(ngram.get(0)) > 0;
          }
          if (ngram.size() == 2) {
            return bagSubsetOk_.bagOfBigrams().count(ngram) > 0;
          }
          if (ngram.size() == 3) {
            return bagSubsetOk_.bagOfTrigrams().count(ngram) > 0;
          }
          return false;
        }).map(ngram -> Joiner.on(' ').join(ngram)).collect(Collectors.toSet());
  }

  @Override
  protected double computeX(String text, String candidate) {

    List<String> words = Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(candidate);

    double freqOk; // the higher, the better

    if (words.size() == 1) {
      freqOk = bagSubsetOk_.documentFrequency(words.get(0));
    } else if (words.size() == 2) {
      freqOk = bagSubsetOk_.documentFrequency(words.get(0), words.get(1));
    } else { // if (words.size() == 3) {
      freqOk = bagSubsetOk_.documentFrequency(words.get(0), words.get(1), words.get(2));
    }

    if (!Double.isFinite(freqOk) || freqOk == 0.0) {
      return 0.0000001;
    }
    return freqOk; // the higher, the better
  }

  @Override
  protected double computeY(String text, String candidate) {

    List<String> words = Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(candidate);

    double freqKo; // the lower, the better

    if (words.size() == 1) {
      freqKo = bagSubsetKo_.documentFrequency(words.get(0));
    } else if (words.size() == 2) {
      freqKo = bagSubsetKo_.documentFrequency(words.get(0), words.get(1));
    } else { // if (words.size() == 3) {
      freqKo = bagSubsetKo_.documentFrequency(words.get(0), words.get(1), words.get(2));
    }

    if (!Double.isFinite(freqKo) || freqKo == 0.0) {
      return 1.0;
    }
    return 1.0 - freqKo; // the higher, the better
  }

  @Override
  protected List<Map.Entry<String, Double>> filter(
      @NotNull List<Map.Entry<String, Double>> candidates) {

    List<Map.Entry<List<String>, Double>> candidatesNew = candidates.stream()
        .map(candidate -> new AbstractMap.SimpleEntry<>(
            Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(candidate.getKey()),
            candidate.getValue()))
        .collect(Collectors.toList());

    List<Map.Entry<List<String>, Double>> candidatesFiltered = new ArrayList<>();

    for (int i = 0; i < candidatesNew.size(); i++) {

      List<String> candidate1 = candidatesNew.get(i).getKey();
      int middle1 = (candidate1.size() / 2) + 1;

      for (int j = i + 1; j < candidatesNew.size();) {

        List<String> candidate2 = candidatesNew.get(j).getKey();
        int middle2 = (candidate2.size() / 2) + 1;

        List<List<String>> overlaps1 = overlaps(candidate1, candidate2);
        List<List<String>> overlaps2 = overlaps(candidate2, candidate1);

        int max1 = overlaps1.stream().mapToInt(List::size).max().orElse(0);
        int max2 = overlaps2.stream().mapToInt(List::size).max().orElse(0);
        int max = Math.max(max1, max2);

        if (max >= middle1 || max >= middle2) {
          candidatesNew.remove(j);
        } else {
          j++;
        }
      }

      candidatesFiltered.add(candidatesNew.get(i));
    }
    return candidatesFiltered.stream()
        .map(candidate -> new AbstractMap.SimpleEntry<>(Joiner.on(' ').join(candidate.getKey()),
            candidate.getValue()))
        .collect(Collectors.toList());
  }

  /**
   * Returns all suffixes of list1 that are also a prefix of list2.
   *
   * @param list1 first list.
   * @param list2 second list.
   * @return overlapping elements.
   */
  private List<List<String>> overlaps(List<String> list1, List<String> list2) {

    Preconditions.checkNotNull(list1, "list1 should not be null");
    Preconditions.checkNotNull(list2, "list2 should not be null");

    List<List<String>> overlaps = new ArrayList<>();

    for (int j = 0; j < list2.size(); j++) {

      if (list1.size() - 1 - j < 0) {
        return overlaps;
      }

      @Var
      boolean overlap = true;
      List<String> suffix = list1.subList(list1.size() - 1 - j, list1.size());
      List<String> prefix = list2.subList(0, j + 1);

      for (int k = 0; k < suffix.size(); k++) {
        if (!suffix.get(k).equals(prefix.get(k))) {
          overlap = false;
          break;
        }
      }

      if (overlap) {
        overlaps.add(prefix);
      }
    }
    return overlaps;
  }

  /**
   * Compute and cache unigrams extracted from a text.
   *
   * @param text text.
   * @return distinct unigrams.
   */
  private Set<List<String>> corpusTextUnigrams(String text) {

    Preconditions.checkNotNull(text, "text should not be null");

    return bagCorpus_.text(text).bagOfWords().elementSet().stream()
        .map(word -> Lists.newArrayList(word)).collect(Collectors.toSet());
  }

  /**
   * Compute and cache bigrams extracted from a text.
   *
   * @param text text.
   * @return distinct bigrams.
   */
  private Set<List<String>> corpusTextBigrams(String text) {

    Preconditions.checkNotNull(text, "text should not be null");

    Text txt = bagCorpus_.text(text);

    return txt.bigrams(null, null, '¤').stream()
        .map(bigram -> Splitter.on('¤').trimResults().omitEmptyStrings().splitToList(bigram))
        .collect(Collectors.toSet());
  }

  /**
   * Compute and cache trigrams extracted from a text.
   *
   * @param text text.
   * @return distinct trigrams.
   */
  private Set<List<String>> corpusTextTrigrams(String text) {

    Preconditions.checkNotNull(text, "text should not be null");

    Text txt = bagCorpus_.text(text);

    return txt.trigrams(null, null, null, '¤').stream()
        .map(trigram -> Splitter.on('¤').trimResults().omitEmptyStrings().splitToList(trigram))
        .collect(Collectors.toSet());
  }

  /**
   * Compute TF-IDF for ngrams.
   *
   * @param bag bag of texts.
   * @param text text.
   * @param word1 first word.
   * @param word2 second word.
   * @param word3 third word.
   * @return TF-IDF.
   */
  @Deprecated
  private double tfIdf(IBagOfTexts bag, String text, String word1, String word2, String word3) {

    Preconditions.checkNotNull(bag, "bag should not be null");
    Preconditions.checkNotNull(text, "text should not be null");
    Preconditions.checkNotNull(word1, "word1 should not be null");

    Text txt = bag.text(text);

    if (txt == null) {
      return 0.0;
    }

    if (word2 == null && word3 == null) {
      return bag.tfIdf(txt, word1);
    }
    if (word2 != null && word3 == null) {
      return bag.tfIdf(txt, word1, word2);
    }
    if (word2 != null && word3 != null) {
      return bag.tfIdf(txt, word1, word2, word3);
    }
    return 0.0;
  }
}
