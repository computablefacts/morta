package com.computablefacts.morta;

import static com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction.ABSTAIN;

import java.io.File;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.computablefacts.asterix.IO;
import com.computablefacts.asterix.SnippetExtractor;
import com.computablefacts.asterix.StringIterator;
import com.computablefacts.asterix.View;
import com.computablefacts.asterix.codecs.StringCodec;
import com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.labelmodels.TreeLabelModel;
import com.computablefacts.nona.helpers.Strings;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
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

  public static <T> void serialize(String filename, T t) {

    Preconditions.checkNotNull(t, "t should not be null");
    Preconditions.checkNotNull(filename, "filename should not be null");

    Preconditions.checkState(IO.writeCompressedText(new File(filename), xStream().toXML(t), false),
        "%s cannot be written", filename);
  }

  @SuppressWarnings("unchecked")
  public static <T> T deserialize(String filename) {

    Preconditions.checkNotNull(filename, "filename should not be null");

    return (T) xStream().fromXML(String.join("\n", View.of(new File(filename), true).toList()));
  }

  public static DecimalFormat decimalFormat() {

    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
    symbols.setDecimalSeparator('.');

    DecimalFormat df = new DecimalFormat("#.##", symbols);
    df.setGroupingUsed(false);
    df.setRoundingMode(RoundingMode.UP);

    return df;
  }

  /**
   * For each data point, get the label output by each labeling functions.
   *
   * @param lfs labeling functions.
   * @return pairs of (data point, {@link FeatureVector}). Each column of the {@link FeatureVector}
   *         represents a distinct labeling function output. The first feature is the output of the
   *         first labeling function, the second feature is the output of the second labeling
   *         function, etc. Thus, the {@link FeatureVector} length is equal to the number of
   *         labeling functions.
   */
  public static <D> Function<D, Map.Entry<D, FeatureVector<Integer>>> label(
      List<? extends AbstractLabelingFunction<D>> lfs) {

    Preconditions.checkNotNull(lfs, "lfs should not be null");

    return d -> {

      FeatureVector<Integer> vector = new FeatureVector<>(lfs.size(), ABSTAIN);

      for (int i = 0; i < lfs.size(); i++) {
        AbstractLabelingFunction<D> lf = lfs.get(i);
        int label = lf.apply(d);
        vector.set(i, label);
      }
      return new AbstractMap.SimpleEntry<>(d, vector);
    };
  }

  @Deprecated
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

  @Deprecated
  public static String[][] vectors(TreeLabelModel<String> labelModel,
      List<IGoldLabel<String>> goldLabels) {

    Preconditions.checkNotNull(labelModel, "labelModel should not be null");
    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    com.computablefacts.morta.Dictionary lfNames = labelModel.lfNames();
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

  public static Multiset<String>[] ngrams(int maxGroupSize, String text) {

    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");
    Preconditions.checkNotNull(text, "text should not be null");

    Multiset<String>[] ngrams = new Multiset[5];

    for (int i = 0; i < 5; i++) {
      ngrams[i] = HashMultiset.create();
    }

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

          ngrams[0].add(w5);

          if (maxGroupSize >= 2) {
            ngrams[1].add(w4 + w5);
          }
          if (maxGroupSize >= 3) {
            ngrams[2].add(w3 + w4 + w5);
          }
          if (maxGroupSize >= 4) {
            ngrams[3].add(w2 + w3 + w4 + w5);
          }
          if (maxGroupSize >= 5) {
            ngrams[4].add(w1 + w2 + w3 + w4 + w5);
          }
        }
        word.setLength(0);
        word.append('_');
      }
    }
    return ngrams;
  }

  public static Map<String, Double> features(int maxGroupSize, String text) {

    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");
    Preconditions.checkNotNull(text, "text should not be null");

    Multiset<String>[] ngrams = ngrams(maxGroupSize, text);
    Multiset<String>[] patterns = patterns(ngrams);
    Map<String, Double> features = new HashMap<>();

    Arrays.stream(patterns).flatMap(p -> p.elementSet().stream()).forEach(p -> {

      OptionalDouble max = Arrays.stream(patterns).filter(pat -> pat.contains(p))
          .mapToDouble(pat -> (double) pat.count(p) / (double) pat.size()).max();

      features.put(p, max.orElse(0.0));
    });
    return features;
  }

  public static List<String> keywords(
      List<? extends AbstractLabelingFunction<String>> labelingFunctions, String text) {

    Preconditions.checkNotNull(labelingFunctions, "labelingFunctions should not be null");
    Preconditions.checkNotNull(text, "text should not be null");

    return labelingFunctions.stream().flatMap((lf) -> lf.matches(text).stream()).distinct()
        .collect(Collectors.toList());
  }

  private static Multiset<String>[] patterns(Multiset<String>[] ngrams) {

    Preconditions.checkNotNull(ngrams, "ngrams should not be null");

    Multiset<String>[] patterns = new Multiset[ngrams.length];

    for (int i = 0; i < ngrams.length; i++) {

      int index = i;

      patterns[index] = HashMultiset.create();

      ngrams[index].entrySet().forEach(entry -> {

        @Var
        String ngram = entry.getElement();
        int count = entry.getCount();

        // Remove 'combining agrave accent' from the original string
        ngram = ngram.replace("\u0300", "");

        // Remove 'combining acute accent' from the original string
        ngram = ngram.replace("\u0301", "");

        String lowercase = ngram.toLowerCase();
        String uppercase = ngram.toUpperCase();
        String normalizedLowercase = StringCodec.removeDiacriticalMarks(lowercase);
        String normalizedUppercase = StringCodec.removeDiacriticalMarks(uppercase);
        StringBuilder builder = new StringBuilder(ngram.length());

        if (ngram.length() != lowercase.length() || ngram.length() != uppercase.length()
            || ngram.length() != normalizedLowercase.length()
            || ngram.length() != normalizedUppercase.length()) {

          // For example the lowercase character 'ß' is mapped to 'SS' in uppercase...
          return;
        }
        for (int k = 0; k < ngram.length(); k++) {
          if (builder.length() == 0 && ngram.charAt(k) == '_') {
            continue; // from our POV, _word <=> word
          }

          char c1 = lowercase.charAt(k);
          char c2 = uppercase.charAt(k);
          char c3 = normalizedLowercase.charAt(k);
          char c4 = normalizedUppercase.charAt(k);

          if (c1 == '_' && c2 == '_') {
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
            if (c1 != c3 && c2 != c3) {
              builder.append(c3);
            }
            if (c1 != c4 && c2 != c4 && c3 != c4) {
              builder.append(c4);
            }
            builder.append(']');
          }
        }

        for (int k = builder.length() - 1; k >= 0; k--) {
          if (builder.charAt(k) != '.' && builder.charAt(k) != '+') {
            builder.setLength(k + 1);
            break; // from our POV, word_ <=> word
          }
        }

        if (builder.length() > 0) {

          String pattern = builder.toString();

          if (!".+".equals(pattern)) {
            patterns[index].add(pattern, count);
          }
        }
      });
    }
    return patterns;
  }

  private static XStream xStream() {

    XStream xStream = new XStream();
    xStream.addPermission(NoTypePermission.NONE);
    xStream.addPermission(NullPermission.NULL);
    xStream.addPermission(PrimitiveTypePermission.PRIMITIVES);
    xStream.allowTypeHierarchy(Collection.class);
    xStream.allowTypesByWildcard(new String[] {"com.computablefacts.**",
        "com.google.common.collect.**", "java.lang.**", "java.util.**", "java.io.File",
        "smile.classification.**", "com.computablefacts.morta.textcat.**"});

    return xStream;
  }
}
