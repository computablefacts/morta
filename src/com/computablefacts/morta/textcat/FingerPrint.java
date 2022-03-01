package com.computablefacts.morta.textcat;

import static com.computablefacts.morta.snorkel.Helpers.ngrams;

import java.util.*;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

@CheckReturnValue
public class FingerPrint extends Hashtable<String, Integer> {

  private final NavigableSet<Map.Entry<String, Integer>> entries_ =
      new TreeSet<>(new NGramEntryComparator());
  private final Map<String, Integer> categoryDistances_ = new HashMap<>();
  private String category_ = "unknown";
  private double avgLength_ = 0.0d;

  public FingerPrint() {}

  @Override
  public String toString() {

    @Var
    String s = "";
    @Var
    Map.Entry<String, Integer> entry;

    for (Iterator<Map.Entry<String, Integer>> iterator = entries_.iterator(); iterator
        .hasNext(); s = s + entry.getKey() + "\t" + entry.getValue() + "\n") {
      entry = iterator.next();
    }
    return s;
  }

  public String category() {
    return category_;
  }

  public void category(String category) {
    category_ = category;
  }

  public double avgLength() {
    return avgLength_;
  }

  public void avgLength(double avgLength) {
    avgLength_ = avgLength;
  }

  public Map<String, Integer> categoryDistances() {
    return categoryDistances_;
  }

  /**
   * Creates a {@link FingerPrint} object from the given input text.
   *
   * <strong>BE WARNED THAT</strong> good results are obtained by passing to this method a full
   * text, together with numbers, punctuation and other text characters. So, if you have - say -
   * HTML, just throw away tags, but leave the rest if you want to obtain precise results:
   * punctuation comes in very handy at determining the language. At some extent, also upper/lower
   * case letters could help.
   *
   * @param text the text upon which the fingerprint should be built.
   */
  public void create(String text) {

    Preconditions.checkNotNull(text, "text should not be null");

    this.clear();

    Arrays.stream(ngrams(5, text))
        .forEach(m -> m.entrySet().forEach(e -> this.put(e.getElement(), e.getCount())));

    if (this.containsKey("_")) {
      int blanksScore = this.remove("_");
      this.put("_", blanksScore / 2);
    }

    entries_.addAll(this.entrySet());
  }

  /**
   * Computes the distance between the current fingerprint and a given fingerprint.
   *
   * @param fp a fingerprint.
   * @return the distance between the two fingerprints.
   */
  public int distance(FingerPrint fp) {
    return distance(fp, -1);
  }

  /**
   * Find out the most likely categories, if any, by comparing the distance from each of the
   * categories.
   *
   * @param categories the list of possible categories.
   * @return the most likely categories.
   */
  @CanIgnoreReturnValue
  public Map<String, Integer> categorize(Collection<FingerPrint> categories) {

    Preconditions.checkNotNull(categories, "categories should not be null");

    @Var
    int minDistance = Integer.MAX_VALUE;
    int unknownNgramDistance =
        (int) categories.stream().mapToInt(Hashtable::size).average().orElse(-1);

    for (FingerPrint fp : categories) {

      int distance = distance(fp, unknownNgramDistance);
      categoryDistances_.put(fp.category(), distance);

      if (distance < minDistance) {
        minDistance = distance;
        category_ = fp.category();
      }
    }
    return categoryDistances_;
  }

  private int position(String ngram) {

    Preconditions.checkNotNull(ngram, "ngram should not be null");

    @Var
    int pos = 1;
    @Var
    int value = entries_.first().getValue();
    @Var
    Map.Entry<String, Integer> entry;
    Iterator<Map.Entry<String, Integer>> iterator = entries_.iterator();

    do {
      if (!iterator.hasNext()) {
        return -1;
      }

      entry = iterator.next();

      if (value != entry.getValue()) {
        value = entry.getValue();
        ++pos;
      }
    } while (!entry.getKey().equals(ngram));

    return pos;
  }

  private int distance(FingerPrint fp, @Var int unknownNgramDistance) {

    Preconditions.checkNotNull(fp, "fp should not be null");

    unknownNgramDistance = unknownNgramDistance < 0 ? fp.size() : unknownNgramDistance;

    @Var
    int distance = 0;
    @Var
    int count = 0;

    for (Map.Entry<String, Integer> entry : entries_) {

      String ngram = entry.getKey();
      ++count;

      if (count > 400) {
        break;
      }
      if (!fp.containsKey(ngram)) {
        distance += unknownNgramDistance;
      } else {
        distance += Math.abs(position(ngram) - fp.position(ngram));
      }
    }
    return distance;
  }

  private final static class NGramEntryComparator
      implements Comparator<Map.Entry<String, Integer>> {

    NGramEntryComparator() {}

    public int compare(Map.Entry<String, Integer> e1, Map.Entry<String, Integer> e2) {
      if (e2.getValue() - e1.getValue() == 0) {
        return (e1.getKey()).length() - (e2.getKey()).length() == 0
            ? (e1.getKey()).compareTo(e2.getKey())
            : (e1.getKey()).length() - (e2.getKey()).length();
      }
      return e2.getValue() - e1.getValue();
    }
  }
}
