package com.computablefacts.morta.docsetlabeler;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.computablefacts.nona.helpers.IBagOfTexts;
import com.computablefacts.nona.helpers.Text;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.google.common.collect.Multiset;
import com.google.errorprone.annotations.CheckReturnValue;

/**
 * A read-only bag-of-texts.
 */
@CheckReturnValue
final public class BagOfTexts implements IBagOfTexts {

  private static final Object NULL = new Object();
  private final IBagOfTexts bagOfTexts_;
  private final Cache<String, Integer> cacheOccurrences_ = CacheBuilder.newBuilder().recordStats()
      .maximumSize(10000).expireAfterWrite(1, TimeUnit.HOURS).build();
  private final Cache<String, Object> cacheTexts_ = CacheBuilder.newBuilder().recordStats()
      .maximumSize(5000).expireAfterWrite(1, TimeUnit.HOURS).build();

  private int cacheNumberOfDistinctTexts_ = -1;

  public BagOfTexts(IBagOfTexts bagOfTexts) {
    bagOfTexts_ = Preconditions.checkNotNull(bagOfTexts, "bagOfTexts should not be null");
  }

  @Override
  public boolean equals(Object obj) {
    return bagOfTexts_.equals(obj);
  }

  @Override
  public int hashCode() {
    return bagOfTexts_.hashCode();
  }

  @Override
  public Multiset<Text> bagOfTexts() {
    return bagOfTexts_.bagOfTexts();
  }

  @Override
  public Multiset<String> bagOfWords() {
    return bagOfTexts_.bagOfWords();
  }

  @Override
  public Multiset<List<String>> bagOfBigrams() {
    return bagOfTexts_.bagOfBigrams();
  }

  @Override
  public Multiset<List<String>> bagOfTrigrams() {
    return bagOfTexts_.bagOfTrigrams();
  }

  @Override
  public Multiset<List<String>> bagOfNGrams() {
    return bagOfTexts_.bagOfNGrams();
  }

  @Override
  public Text text(String text) {

    Preconditions.checkNotNull(text, "text should not be null");

    try {
      Object obj = cacheTexts_.get(text, () -> {
        Text txt = bagOfTexts_.text(text);
        return txt == null ? NULL : txt;
      });
      return obj == NULL ? null : (Text) obj;
    } catch (ExecutionException e) {
      // TODO
    }
    return null;
  }

  @Override
  public int numberOfNGrams(int n) {
    if (n == 1) {
      return bagOfWords().size();
    }
    if (n == 2) {
      return bagOfBigrams().size();
    }
    if (n == 3) {
      return bagOfTrigrams().size();
    }
    return bagOfTexts_.numberOfNGrams(n);
  }

  @Override
  public int numberOfDistinctTexts() {
    if (cacheNumberOfDistinctTexts_ < 0) {
      cacheNumberOfDistinctTexts_ = bagOfTexts_.numberOfDistinctTexts();
    }
    return cacheNumberOfDistinctTexts_;
  }

  @Override
  public int numberOfDistinctTextsOccurrences(String word) {

    Preconditions.checkNotNull(word, "word should not be null");

    try {
      return cacheOccurrences_.get(word, () -> bagOfTexts_.numberOfDistinctTextsOccurrences(word));
    } catch (ExecutionException e) {
      // TODO
    }
    return 0;
  }

  @Override
  public int numberOfDistinctTextsOccurrences(String word1, String word2) {

    Preconditions.checkNotNull(word1, "word1 should not be null");
    Preconditions.checkNotNull(word2, "word2 should not be null");

    try {
      return cacheOccurrences_.get(word1 + "¤" + word2,
          () -> bagOfTexts_.numberOfDistinctTextsOccurrences(word1, word2));
    } catch (ExecutionException e) {
      // TODO
    }
    return 0;
  }

  @Override
  public int numberOfDistinctTextsOccurrences(String word1, String word2, String word3) {

    Preconditions.checkNotNull(word1, "word1 should not be null");
    Preconditions.checkNotNull(word2, "word2 should not be null");
    Preconditions.checkNotNull(word3, "word3 should not be null");

    try {
      return cacheOccurrences_.get(word1 + "¤" + word2 + "¤" + word3,
          () -> bagOfTexts_.numberOfDistinctTextsOccurrences(word1, word2, word3));
    } catch (ExecutionException e) {
      // TODO
    }
    return 0;
  }

  public CacheStats cacheOccurrencesStats() {
    return cacheOccurrences_.stats();
  }

  public CacheStats cacheTextsStats() {
    return cacheTexts_.stats();
  }
}
