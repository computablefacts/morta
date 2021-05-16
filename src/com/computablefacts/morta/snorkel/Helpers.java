package com.computablefacts.morta.snorkel;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
import com.computablefacts.nona.helpers.Languages;
import com.computablefacts.nona.helpers.NGramIterator;
import com.computablefacts.nona.helpers.SnippetExtractor;
import com.computablefacts.nona.helpers.StringIterator;
import com.computablefacts.nona.helpers.Strings;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;

import smile.stat.hypothesis.CorTest;

@CheckReturnValue
final public class Helpers {

  private Helpers() {}

  public static XStream xStream() {

    XStream xStream = new XStream();
    xStream.addPermission(NoTypePermission.NONE);
    xStream.addPermission(NullPermission.NULL);
    xStream.addPermission(PrimitiveTypePermission.PRIMITIVES);
    xStream.allowTypeHierarchy(Collection.class);
    xStream.allowTypesByWildcard(
        new String[] {"com.computablefacts.**", "com.google.common.collect.**", "java.lang.**",
            "java.util.**", "java.io.File", "smile.classification.**"});

    return xStream;
  }

  public static DecimalFormat decimalFormat() {

    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
    symbols.setDecimalSeparator('.');

    DecimalFormat df = new DecimalFormat("#.##", symbols);
    df.setGroupingUsed(false);
    df.setRoundingMode(RoundingMode.UP);

    return df;
  }

  public static ITransformationFunction<String, FeatureVector<Double>> countVectorizer(
      Languages.eLanguage language, Dictionary alphabet, int maxGroupSize) {

    Preconditions.checkNotNull(language, "language should not be null");
    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    return text -> {

      FeatureVector<Double> vector = new FeatureVector<>(alphabet.size(), 0.0);
      Multiset<String> features = features(language, maxGroupSize, text);

      for (int i = 1; i < maxGroupSize; i++) {
        new NGramIterator(i, text, true).forEachRemaining(span -> features.add(span.text()));
      }

      features.entrySet().forEach(ngram -> {

        String word = ngram.getElement();
        int count = ngram.getCount();

        if (alphabet.containsKey(word)) {
          vector.set(alphabet.id(word), (double) count);
        }
      });
      return vector;
    };
  }

  public static String[][] correlations(Table<String, String, CorTest> lfCorrelations) {

    Preconditions.checkNotNull(lfCorrelations, "lfCorrelations should not be null");

    List<String> labels =
        Lists.newArrayList(Sets.union(lfCorrelations.rowKeySet(), lfCorrelations.columnKeySet()));

    DecimalFormat decimalFormat = decimalFormat();
    String[][] matrix = new String[labels.size() + 1][labels.size() + 1];

    for (int i = 0; i < labels.size(); i++) {
      matrix[0][i + 1] = labels.get(i);
      matrix[i + 1][0] = labels.get(i);
    }

    for (int i = 0; i < labels.size(); i++) {
      for (int j = 0; j < labels.size(); j++) {
        matrix[i + 1][j + 1] =
            decimalFormat.format(lfCorrelations.get(labels.get(i), labels.get(j)).cor);
      }
    }
    return matrix;
  }

  public static String[][] vectors(TreeLabelModel<String> labelModel,
      List<? extends IGoldLabel<String>> goldLabels) {

    Preconditions.checkNotNull(labelModel, "labelModel should not be null");
    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    Dictionary lfNames = labelModel.lfNames();
    Dictionary lfLabels = labelModel.lfLabels();
    List<? extends AbstractLabelingFunction<String>> labelingFunctions =
        labelModel.labelingFunctions();
    List<Map.Entry<String, FeatureVector<Integer>>> instances = labelModel.vectors(goldLabels);
    List<String> lfActualLabels = labelModel.actual(goldLabels);
    List<String> lfPredictedLabels = labelModel.predicted(goldLabels);

    Preconditions.checkArgument(instances.size() == lfActualLabels.size());
    Preconditions.checkArgument(instances.size() == lfPredictedLabels.size());
    Preconditions.checkArgument(instances.size() == goldLabels.size());

    int disp = 3;
    String[][] rows = new String[instances.size() + 1][instances.get(0).getValue().size() + disp];

    rows[0][0] = "Actual Label";
    rows[0][1] = "Predicted Label";
    rows[0][2] = "Snippet";

    for (int i = 0; i < lfNames.size(); i++) {
      rows[0][i + disp] = lfNames.label(i);
    }

    @Var
    int u = 1;

    for (int i = 0; i < instances.size(); i++) {

      String actual = lfActualLabels.get(i);
      String predicted = lfPredictedLabels.get(i);

      if (actual.equals(predicted)) {
        continue; // discard instance when the prediction matches the actual
      }

      IGoldLabel<String> goldLabel = goldLabels.get(i);
      List<String> keywords = keywords(labelingFunctions, goldLabel.data());
      FeatureVector<Integer> vector = instances.get(i).getValue();

      Preconditions.checkState(lfNames.size() == vector.size());

      rows[u][0] = actual;
      rows[u][1] = predicted;
      rows[u][2] = keywords.isEmpty() ? ""
          : Strings.encode(SnippetExtractor.extract(keywords, goldLabel.data(), 300, 50, "...")
              .replace("%", "\\u0025"));

      for (int k = 0; k < lfNames.size(); k++) {
        rows[u][k + disp] = lfLabels.label(vector.get(k));
      }

      u++;
    }

    String[][] rowsNew = new String[u][instances.get(0).getValue().size() + disp];

    for (int i = 0; i < u; i++) {
      for (int j = 0; j < rows[i].length; j++) {
        rowsNew[i][j] = rows[i][j];
      }
    }
    return rowsNew;
  }

  public static Multiset<String> features(Languages.eLanguage language, int maxGroupSize,
      String text) {

    Preconditions.checkNotNull(language, "language should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");
    Preconditions.checkNotNull(text, "text should not be null");

    Multiset<String> ngrams = HashMultiset.create();
    StringBuilder word = new StringBuilder();
    @Var
    String w1 = "";
    @Var
    String w2 = "";
    @Var
    String w3 = "";
    @Var
    String w4 = "";
    @Var
    String w5 = "";
    StringIterator iterator = new StringIterator(text);

    while (iterator.hasNext()) {

      char c = iterator.next();

      boolean isApostrophe = StringIterator.isApostrophe(c);
      boolean isArrow = StringIterator.isArrow(c);
      boolean isBracket = StringIterator.isBracket(c);
      boolean isCjkSymbol = StringIterator.isCjkSymbol(c);
      boolean isCurrency = StringIterator.isCurrency(c);
      boolean isDoubleQuotationMark = StringIterator.isDoubleQuotationMark(c);
      boolean isGeneralPunctuation = StringIterator.isGeneralPunctuation(c);
      boolean isNumber = Character.isDigit((int) c);
      boolean isListMark = StringIterator.isListMark(c);
      boolean isPunctuation = StringIterator.isPunctuation(c);
      boolean isQuotationMark = StringIterator.isQuotationMark(c);
      boolean isSeparatorMark = StringIterator.isSeparatorMark(c);
      boolean isSingleQuotationMark = StringIterator.isSingleQuotationMark(c);
      boolean isTerminalMark = StringIterator.isTerminalMark(c);
      boolean isWhitespace = StringIterator.isWhitespace(c);

      if (!isNumber && !isApostrophe && !isArrow && !isWhitespace && !isPunctuation
          && !isGeneralPunctuation && !isCurrency && !isCjkSymbol && !isListMark && !isTerminalMark
          && !isSeparatorMark && !isQuotationMark && !isSingleQuotationMark
          && !isDoubleQuotationMark && !isBracket) {
        word.append(c);
      } else {

        String w = word.toString();

        if (w.length() > 0) {

          w1 = w2;
          w2 = w3;
          w3 = w4;
          w4 = w5;
          w5 = w;

          String w2Masked =
              IntStream.range(1, w2.length() + 1).mapToObj(i -> "_").collect(Collectors.joining());
          String w3Masked =
              IntStream.range(1, w3.length() + 1).mapToObj(i -> "_").collect(Collectors.joining());
          String w4Masked =
              IntStream.range(1, w4.length() + 1).mapToObj(i -> "_").collect(Collectors.joining());

          ngrams.add(w5);

          if (maxGroupSize >= 2) {
            ngrams.add(w4 + w5);
          }
          if (maxGroupSize >= 3) {
            ngrams.add(w3 + w4 + w5);
            ngrams.add(w3 + w4Masked + w5);
          }
          if (maxGroupSize >= 4) {
            ngrams.add(w2 + w3 + w4 + w5);
            ngrams.add(w2 + w3Masked + w4 + w5);
            ngrams.add(w2 + w3 + w4Masked + w5);
            ngrams.add(w2 + w3Masked + w4Masked + w5);
          }
          if (maxGroupSize >= 5) {
            ngrams.add(w1 + w2 + w3 + w4 + w5);
            ngrams.add(w1 + w2Masked + w3 + w4 + w5);
            ngrams.add(w1 + w2 + w3Masked + w4 + w5);
            ngrams.add(w1 + w2 + w3 + w4Masked + w5);
            ngrams.add(w1 + w2Masked + w3Masked + w4 + w5);
            ngrams.add(w1 + w2Masked + w3 + w4Masked + w5);
            ngrams.add(w1 + w2 + w3Masked + w4Masked + w5);
            ngrams.add(w1 + w2Masked + w3Masked + w4Masked + w5);
          }
        }
        word.setLength(0);
        word.append('_');
      }
    }
    return patterns(ngrams);
  }

  public static List<String> keywords(
      List<? extends AbstractLabelingFunction<String>> labelingFunctions, String text) {

    Preconditions.checkNotNull(labelingFunctions, "labelingFunctions should not be null");
    Preconditions.checkNotNull(text, "text should not be null");

    return labelingFunctions.stream().flatMap((lf) -> lf.matches(text).stream()).distinct()
        .collect(Collectors.toList());
  }

  private static Multiset<String> patterns(Multiset<String> ngrams) {

    Preconditions.checkNotNull(ngrams, "ngrams should not be null");

    Multiset<String> patterns = HashMultiset.create();

    ngrams.entrySet().forEach(entry -> {

      String ngram = entry.getElement();
      int count = entry.getCount();

      String lowercase = ngram.toLowerCase();
      String uppercase = ngram.toUpperCase();
      String noDiacritics = StringIterator.removeDiacriticalMarks(ngram);
      StringBuilder builder = new StringBuilder(ngram.length());

      for (int i = 0; i < ngram.length(); i++) {
        if (builder.length() == 0 && ngram.charAt(i) == '_') {
          continue; // from our POV, _word <=> word
        }

        char c1 = lowercase.charAt(i);
        char c2 = uppercase.charAt(i);
        char c3 = noDiacritics.charAt(i);

        if (c1 == '_' && c2 == '_' && c3 == '_') {
          if (builder.length() > 0) {
            char prev = builder.charAt(builder.length() - 1);
            if (prev == '.') {
              builder.append('+');
            } else if (prev != '+') {
              builder.append('.');
            }
          }
        } else {
          builder.append('[');
          builder.append(c1);
          if (c1 != c2) {
            builder.append(c2);
          }
          if (c1 != c2 && c1 != c3 && c2 != c3) {
            builder.append(c3);
          }
          builder.append(']');
        }
      }

      for (int i = builder.length() - 1; i >= 0; i--) {
        if (builder.charAt(i) != '.' && builder.charAt(i) != '+') {
          builder.setLength(i + 1);
          break; // from our POV, word_ <=> word
        }
      }

      if (builder.length() > 0) {

        String pattern = builder.toString();

        if (!".+".equals(pattern)) {
          patterns.add(pattern, count);
        }
      }
    });
    return patterns;
  }
}
