package com.computablefacts.morta.poc;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

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
  protected Set<String> candidates(@NotNull List<String> corpus, @NotNull List<String> subsetOk,
      @NotNull List<String> subsetKo, String text) {

    Set<List<String>> ngrams = Sets
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
        }).collect(Collectors.toSet());

    Set<List<String>> unigrams =
        ngrams.stream().filter(ngram -> ngram.size() == 1).collect(Collectors.toSet());

    Set<List<String>> bigrams = ngrams.stream().filter(ngram -> ngram.size() == 2).peek(ngram -> {

      // Discard unigrams that belong to at least one bigram
      unigrams.removeIf(n -> ngram.get(0).equals(n.get(0)) || ngram.get(1).equals(n.get(0)));
    }).collect(Collectors.toSet());

    Set<List<String>> trigrams = ngrams.stream().filter(ngram -> ngram.size() == 3).peek(ngram -> {

      String word0 = ngram.get(0);
      String word1 = ngram.get(1);
      String word2 = ngram.get(2);

      // Discard unigrams that belong to at least one trigram
      unigrams.removeIf(
          n -> word0.equals(n.get(0)) || word1.equals(n.get(0)) || word2.equals(n.get(0)));

      // Discard bigrams that belong to at least one trigram
      bigrams.removeIf(n -> (word0.equals(n.get(0)) && word1.equals(n.get(1)))
          || (word1.equals(n.get(0)) && word2.equals(n.get(1))));
    }).collect(Collectors.toSet());

    return Sets.union(unigrams, Sets.union(bigrams, trigrams)).stream()
        .map(ngram -> Joiner.on(' ').join(ngram)).collect(Collectors.toSet());
  }

  @Override
  protected double computeX(@NotNull List<String> corpus, @NotNull List<String> subsetOk,
      @NotNull List<String> subsetKo, Set<String> candidates, String text, String candidate) {

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
  protected double computeY(@NotNull List<String> corpus, @NotNull List<String> subsetOk,
      @NotNull List<String> subsetKo, Set<String> candidates, String text, String candidate) {

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
