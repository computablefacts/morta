package com.computablefacts.morta.poc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.tartarus.snowball.SnowballStemmer;

import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.BagOfTexts;
import com.computablefacts.nona.helpers.DocSetLabeler;
import com.computablefacts.nona.helpers.IBagOfTexts;
import com.computablefacts.nona.helpers.Languages;
import com.computablefacts.nona.helpers.Text;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CheckReturnValue;

/**
 * Guesstimate interesting patterns from positively/negatively annotated texts.
 */
@CheckReturnValue
final public class TfIdfDocSetLabeler extends DocSetLabeler {

  private final Languages.eLanguage language_;
  private final SnowballStemmer stemmer_;
  private final Set<String> stopwords_;

  private IBagOfTexts bagCorpus_ = null;
  private IBagOfTexts bagSubsetOk_ = null;
  private IBagOfTexts bagSubsetKo_ = null;

  public TfIdfDocSetLabeler(Languages.eLanguage language) {

    Preconditions.checkNotNull(language, "language should not be null");

    language_ = language;
    stemmer_ = Languages.stemmer(language);
    stopwords_ = Languages.stopwords(language).stream().map(stopword -> {
      stemmer_.setCurrent(stopword);
      if (stemmer_.stem()) {
        return stemmer_.getCurrent();
      }
      return stopword;
    }).collect(Collectors.toSet());
  }

  @Override
  protected void init(@NotNull Set<String> corpus, @NotNull Set<String> subsetOk,
      @NotNull Set<String> subsetKo) {
    if (bagCorpus_ == null) {

      AtomicInteger count = new AtomicInteger(0);
      AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
      BagOfTexts bag = new BagOfTexts(Helpers.sentenceSplitter(), Helpers.wordSplitter(language_));

      corpus.stream().peek(
          t -> bar.update(count.incrementAndGet(), corpus.size(), "Loading the whole corpus..."))
          .forEach(bag::add);

      bagCorpus_ = new com.computablefacts.morta.poc.BagOfTexts(bag.freezeBagOfTexts());

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

      bagSubsetOk_ = new com.computablefacts.morta.poc.BagOfTexts(bag.freezeBagOfTexts());

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

      bagSubsetKo_ = new com.computablefacts.morta.poc.BagOfTexts(bag.freezeBagOfTexts());

      System.out.println(); // Cosmetic
    }
  }

  @Override
  protected void uinit() {
    bagSubsetOk_ = null;
    bagSubsetKo_ = null;
  }

  @Override
  protected Set<String> candidates(@NotNull Set<String> corpus, @NotNull Set<String> subsetOk,
      @NotNull Set<String> subsetKo, String text) {

    Set<List<String>> unigrams = corpusTextUnigrams(text);
    Set<List<String>> bigrams = corpusTextBigrams(text, unigrams);
    Set<List<String>> trigrams = corpusTextTrigrams(text, unigrams);

    return Sets.union(unigrams, Sets.union(bigrams, trigrams)).stream().filter(ngram -> {

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
    }).filter(ngram -> {

      double freq;

      if (ngram.size() == 1) {
        freq = bagCorpus_.documentFrequency(ngram.get(0));
      } else if (ngram.size() == 2) {
        freq = bagCorpus_.documentFrequency(ngram.get(0), ngram.get(1));
      } else if (ngram.size() == 3) {
        freq = bagCorpus_.documentFrequency(ngram.get(0), ngram.get(1), ngram.get(2));
      } else {
        freq = 0.0;
      }

      // Remove ngrams that appear in less than 1% of all documents
      // Remove ngrams that appear in more than 50% of all documents
      return freq > 0.01 && freq < 0.50;
    }).map(ngram -> Joiner.on(' ').join(ngram)).collect(Collectors.toSet());
  }

  @Override
  protected double computeX(@NotNull Set<String> corpus, @NotNull Set<String> subsetOk,
      @NotNull Set<String> subsetKo, Set<String> candidates, String text, String candidate) {

    List<String> words = Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(candidate);

    double freqOk; // the higher, the better

    if (words.size() == 1) {
      freqOk = bagSubsetOk_.normalizedFrequency(words.get(0));
    } else if (words.size() == 2) {
      freqOk = bagSubsetOk_.normalizedFrequency(words.toArray(new String[0]));
    } else { // if (words.size() == 3) {
      freqOk = bagSubsetOk_.normalizedFrequency(words.toArray(new String[0]));
    }

    if (!Double.isFinite(freqOk) || freqOk == 0.0) {
      return 0.0;
    }

    double freqKo; // the lower, the better

    if (words.size() == 1) {
      freqKo = bagSubsetKo_.normalizedFrequency(words.get(0));
    } else if (words.size() == 2) {
      freqKo = bagSubsetKo_.normalizedFrequency(words.toArray(new String[0]));
    } else { // if (words.size() == 3) {
      freqKo = bagSubsetKo_.normalizedFrequency(words.toArray(new String[0]));
    }

    if (!Double.isFinite(freqKo) || freqKo == 0.0) {
      return freqOk;
    }
    return freqOk / freqKo;
  }

  @Override
  protected double computeY(@NotNull Set<String> corpus, @NotNull Set<String> subsetOk,
      @NotNull Set<String> subsetKo, Set<String> candidates, String text, String candidate) {

    List<String> words = Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(candidate);

    double tfIdf;

    if (words.size() == 1) {
      tfIdf = tfIdf(bagCorpus_, text, words.get(0), null, null);
    } else if (words.size() == 2) {
      tfIdf = tfIdf(bagCorpus_, text, words.get(0), words.get(1), null);
    } else { // if (words.size() == 3) {
      tfIdf = tfIdf(bagCorpus_, text, words.get(0), words.get(1), words.get(2));
    }

    if (!Double.isFinite(tfIdf) || tfIdf == 0.0) {
      return 0.0;
    }
    return tfIdf;
  }

  /**
   * Compute and cache unigrams extracted from a text.
   *
   * @param text text.
   * @return distinct unigrams.
   */
  private Set<List<String>> corpusTextUnigrams(String text) {

    Preconditions.checkNotNull(text, "text should not be null");

    return bagCorpus_.text(text).bagOfWords().elementSet().stream().filter(word -> {

      // Remove stopwords
      return !stopwords_.contains(word);
    }).filter(word -> {

      // Remove very small words
      return word.length() >= 3;
    }).filter(word -> {

      // Remove numbers
      return !com.computablefacts.nona.helpers.Strings.isNumber(word);
    }).map(word -> Lists.newArrayList(word)).collect(Collectors.toSet());
  }

  /**
   * Compute and cache bigrams extracted from a text.
   *
   * @param text text.
   * @return distinct bigrams.
   */
  private Set<List<String>> corpusTextBigrams(String text, Set<List<String>> unigrams) {

    Preconditions.checkNotNull(text, "text should not be null");
    Preconditions.checkNotNull(unigrams, "unigrams should not be null");

    Set<List<String>> bigrams = new HashSet<>();

    Text txt = bagCorpus_.text(text);

    for (List<String> unigram1 : unigrams) {
      for (List<String> unigram2 : unigrams) {
        txt.bigrams(unigram1.get(0), unigram2.get(0), '¤').entrySet()
            .forEach(trigram -> bigrams.add(Splitter.on('¤').trimResults().omitEmptyStrings()
                .splitToList(trigram.getElement())));
      }
    }
    return bigrams;
  }

  /**
   * Compute and cache trigrams extracted from a text.
   *
   * @param text text.
   * @return distinct trigrams.
   */
  private Set<List<String>> corpusTextTrigrams(String text, Set<List<String>> unigrams) {

    Preconditions.checkNotNull(text, "text should not be null");
    Preconditions.checkNotNull(unigrams, "unigrams should not be null");

    Set<List<String>> trigrams = new HashSet<>();

    Text txt = bagCorpus_.text(text);

    for (List<String> unigram1 : unigrams) {
      for (List<String> unigram2 : unigrams) {
        txt.trigrams(unigram1.get(0), null, unigram2.get(0), '¤').entrySet()
            .forEach(trigram -> trigrams.add(Splitter.on('¤').trimResults().omitEmptyStrings()
                .splitToList(trigram.getElement())));
      }
    }
    return trigrams.stream().filter(ngram -> {

      // Ignore trigrams whose middle word is a number
      return ngram.size() == 2 || (ngram.size() == 3
          && !com.computablefacts.nona.helpers.Strings.isNumber(ngram.get(1)));
    }).collect(Collectors.toSet());
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
