package com.computablefacts.morta.snorkel;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.tartarus.snowball.SnowballStemmer;

import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.labelmodels.MedianLabelModel;
import com.computablefacts.nona.helpers.Languages;
import com.computablefacts.nona.helpers.SnippetExtractor;
import com.computablefacts.nona.helpers.StringIterator;
import com.computablefacts.nona.helpers.Strings;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
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

  public static Function<String, List<String>> sentenceSplitter() {
    return s -> Splitter.on(CharMatcher.anyOf("!?.,;:")).trimResults().omitEmptyStrings()
        .splitToList(normalize(s));
  }

  public static Function<String, List<String>> wordSplitter(Languages.eLanguage language) {

    Preconditions.checkNotNull(language, "language should not be null");

    SnowballStemmer stemmer = Languages.stemmer(language);
    Set<String> stopwords = Languages.stopwords(language);

    return s -> Splitter.on(CharMatcher.whitespace().or(CharMatcher.breakingWhitespace()))
        .trimResults().omitEmptyStrings().splitToList(s).stream()
        .filter(word -> stopwords == null || !stopwords.contains(word)).map(word -> {
          stemmer.setCurrent(word);
          if (stemmer.stem()) {
            return stemmer.getCurrent();
          }
          return word;
        }).collect(Collectors.toList());
  }

  public static Consumer<String> alphabetBuilder(Languages.eLanguage language, Dictionary alphabet,
      Multiset<String> counts, int maxGroupSize) {

    Preconditions.checkNotNull(language, "language should not be null");
    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkNotNull(counts, "counts should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    Function<String, List<String>> sentenceSplitter = Helpers.sentenceSplitter();
    Function<String, List<String>> wordSplitter = Helpers.wordSplitter(language);

    return text -> {

      List<List<String>> sentences = sentenceSplitter.apply(text).stream()
          .map(sentence -> wordSplitter.apply(sentence)).collect(Collectors.toList());

      for (int i = 0; i < sentences.size(); i++) {

        List<String> sentence = sentences.get(i);

        for (int j = 0; j < sentence.size(); j++) {
          for (int l = j + 1; l < sentence.size() && l < j + maxGroupSize; l++) {

            String ngram = Joiner.on(' ').join(sentence.subList(j, l));

            if (!alphabet.containsKey(ngram)) {
              alphabet.put(ngram, alphabet.size());
            }
            counts.add(ngram);
          }
        }
      }
    };
  }

  public static Dictionary alphabetReducer(Dictionary alphabet, Multiset<String> counts,
      int nbGoldLabels) {

    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkNotNull(counts, "counts should not be null");
    Preconditions.checkArgument(nbGoldLabels > 0, "nbGoldLabels must be > 0");

    Dictionary newAlphabet = new Dictionary();
    int minDf = (int) (0.01 * nbGoldLabels); // remove outliers
    int maxDf = (int) (0.99 * nbGoldLabels); // remove outliers
    @Var
    int disp = 0;

    for (int i = 0; i < alphabet.size(); i++) {

      String ngram = alphabet.label(i);

      if (counts.count(ngram) < minDf) {
        disp++;
        continue;
      }
      if (counts.count(ngram) > maxDf) {
        disp++;
        continue;
      }
      newAlphabet.put(ngram, i - disp);
    }
    return newAlphabet;
  }

  public static ITransformationFunction<String, FeatureVector<Double>> countVectorizer(
      Languages.eLanguage language, Dictionary alphabet, int maxGroupSize) {

    Preconditions.checkNotNull(language, "language should not be null");
    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    Function<String, List<String>> sentenceSplitter = Helpers.sentenceSplitter();
    Function<String, List<String>> wordSplitter = Helpers.wordSplitter(language);

    return text -> {

      FeatureVector<Double> vector = new FeatureVector<>(alphabet.size(), 0.0);

      List<List<String>> sentences = sentenceSplitter.apply(text).stream()
          .map(sentence -> wordSplitter.apply(sentence)).collect(Collectors.toList());

      for (int i = 0; i < sentences.size(); i++) {

        List<String> sentence = sentences.get(i);

        for (int j = 0; j < sentence.size(); j++) {
          for (int l = j + 1; l < sentence.size() && l < j + maxGroupSize; l++) {

            String ngram = Joiner.on(' ').join(sentence.subList(j, l));

            if (alphabet.containsKey(ngram)) {
              vector.set(alphabet.id(ngram), vector.get(alphabet.id(ngram)) + 1.0);
            }
          }
        }
      }
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

  public static String[][] vectors(MedianLabelModel<String> labelModel,
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
      rows[u][2] =
          Strings.encode(SnippetExtractor.extract(keywords, goldLabel.data(), 300, 50, "..."));

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

  public static String normalize(String text) {

    Preconditions.checkNotNull(text, "text should not be null");

    // https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html
    // \p{Punct} -> Punctuation: One of !"#$%&'()*+,-./:;<=>?@[\]^_`{|}~
    return StringIterator.removeDiacriticalMarks(text).replaceAll("[^!?.,;:\\p{Alnum}\\p{L}]", " ")
        .replaceAll("\\s+", " ").toLowerCase();
  }

  public static List<String> keywords(
      List<? extends AbstractLabelingFunction<String>> labelingFunctions, String text) {

    Preconditions.checkNotNull(labelingFunctions, "labelingFunctions should not be null");
    Preconditions.checkNotNull(text, "text should not be null");

    return labelingFunctions.stream().flatMap((lf) -> lf.matches(text).stream()).flatMap(
        keyword -> Splitter.on(' ').omitEmptyStrings().trimResults().splitToList(keyword).stream())
        .distinct().collect(Collectors.toList());
  }
}
