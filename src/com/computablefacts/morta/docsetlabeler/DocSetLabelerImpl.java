package com.computablefacts.morta.docsetlabeler;

import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.textcat.TextCategorizer;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

/**
 * Guesstimate interesting patterns from positively/negatively annotated texts.
 */
@CheckReturnValue
final public class DocSetLabelerImpl extends DocSetLabeler {

  private final Languages.eLanguage language_;
  private final int maxGroupSize_;
  private final TextCategorizer categorizer_;
  private final int length_;
  private final int max_;

  private final Set<String> boosters_ = new HashSet<>();

  public DocSetLabelerImpl(Languages.eLanguage language, int maxGroupSize,
      Multiset<String> boosters, TextCategorizer categorizer, int length) {

    Preconditions.checkNotNull(language, "language should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");
    Preconditions.checkNotNull(boosters, "boosters must be > 0");
    Preconditions.checkNotNull(categorizer, "categorizer must be > 0");

    language_ = language;
    maxGroupSize_ = maxGroupSize;
    categorizer_ = categorizer;
    length_ = length;
    boosters_.addAll(boosters);
    max_ = boosters_.stream().mapToInt(String::length).max().orElse(0);
  }

  @Override
  protected void init(@NotNull List<String> corpus, @NotNull List<String> subsetOk,
      @NotNull List<String> subsetKo) {}

  @Override
  protected void uinit() {}

  @Override
  protected Set<String> candidates(String text) {

    Map<String, Double> features = Helpers.features(language_, maxGroupSize_, text);
    Set<String> intersection = Sets.intersection(features.keySet(), boosters_);
    List<String> list = new ArrayList<>(intersection);
    list.sort(Comparator.comparingInt(String::length).reversed());

    return Sets.newHashSet(list.subList(0, Math.min(list.size(), 20)));
  }

  @Override
  protected double computeX(String text, String candidate) {
    if (boosters_.contains(candidate)) {
      return (double) candidate.length() / (double) max_;
    }
    return Double.MIN_VALUE;
  }

  @Override
  protected double computeY(String text, String candidate) {

    Set<String> set = new HashSet<>();

    if (!Strings.isNullOrEmpty(text)) {

      Pattern pattern = Pattern.compile(candidate, Pattern.MULTILINE | Pattern.DOTALL);
      Matcher matcher = pattern.matcher(text);

      while (matcher.find()) {

        int start = matcher.start();
        int end = matcher.end();

        int newStart = Math.max(0, start - ((length_ - (end - start)) / 2));
        int newEnd = Math.min(text.length(), end + ((length_ - (end - start)) / 2));

        set.add(text.substring(newStart, newEnd));
      }
    }
    if (set.isEmpty()) {
      return Double.MIN_VALUE;
    }

    Set<String> ok = set.stream()
        .filter(span -> "OK".equals(categorizer_.categorize(span.replaceAll("\\s+", " "))))
        .collect(Collectors.toSet());

    if (!ok.isEmpty()) {
      return 1.0;
    }
    return Double.MIN_VALUE;
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
