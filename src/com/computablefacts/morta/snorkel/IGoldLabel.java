package com.computablefacts.morta.snorkel;

import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.View;
import com.computablefacts.asterix.codecs.JsonCodec;
import com.computablefacts.asterix.console.AsciiProgressBar;
import com.computablefacts.morta.Observations;
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
import com.computablefacts.morta.spacy.AnnotatedText;
import com.computablefacts.morta.spacy.Meta;
import com.computablefacts.morta.spacy.Span;
import com.computablefacts.morta.spacy.Token;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.Var;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

public interface IGoldLabel<D> {

  @Deprecated
  static void toSpacyAnnotations(File input, File output, String label) {

    Preconditions.checkNotNull(input, "input should not be null");
    Preconditions.checkArgument(input.exists(), "input should exist : %s", input);
    Preconditions.checkNotNull(output, "output should not be null");
    Preconditions.checkArgument(!output.exists(), "output should not exist : %s", output);

    View.of(input, true).filter(str -> !Strings.isNullOrEmpty(str))
        .map(str -> (IGoldLabel<String>) new GoldLabel(JsonCodec.asObject(str)))
        .filter(gl -> !Strings.isNullOrEmpty(gl.snippet()))
        .filter(gl -> label == null || label.equals(gl.label())).map(gl -> {
          Meta meta =
              new Meta(gl.id(), gl.label(), TreeLabelModel.label(gl) == OK ? "accept" : "reject");
          return new AnnotatedText(meta, gl.snippet());
        }).toFile(JsonCodec::asString, output, false);
  }

  @Deprecated
  static List<IGoldLabel<String>> fromSpacyAnnotations(Observations observations, File file,
      String label) {

    Preconditions.checkNotNull(observations, "observations should not be null");
    Preconditions.checkNotNull(file, "file should not be null");
    Preconditions.checkArgument(file.exists(), "file should exist : %s", file);

    observations.add("Loading spacy annotations...");

    ObjectMapper mapper = new ObjectMapper();
    AsciiProgressBar.IndeterminateProgressBar bar = AsciiProgressBar.createIndeterminate();

    List<IGoldLabel<String>> gls = View.of(file).index()
        .filter(e -> !Strings.isNullOrEmpty(e.getValue())).peek(e -> bar.update()).map(e -> {

          String json = e.getValue();
          AnnotatedText annotatedText;

          try {
            annotatedText = mapper.readValue(json, AnnotatedText.class);
          } catch (Exception ex) {
            System.out.println(Throwables.getStackTraceAsString(Throwables.getRootCause(ex)));
            return Lists.<IGoldLabel<String>>newArrayList();
          }
          if ("ignore".equals(annotatedText.answer_)) {
            return Lists.<IGoldLabel<String>>newArrayList();
          }

          String id = annotatedText.meta_.source_
              .substring(annotatedText.meta_.source_.lastIndexOf('/') + 1);

          if (annotatedText.spans_ == null || annotatedText.spans_.isEmpty()) {
            return Lists.newArrayList((IGoldLabel<String>) new GoldLabel(id,
                annotatedText.meta_.expectedLabel_, annotatedText.text_, annotatedText.text_,
                "accept".equals(annotatedText.answer_), false,
                "reject".equals(annotatedText.answer_), false));
          }
          return annotatedText.spans_.stream()
              .map(span -> (IGoldLabel<String>) new GoldLabel(id, span.label_, annotatedText.text_,
                  annotatedText.text_.substring(span.start_, span.end_), true, false, false, false))
              .collect(Collectors.toList());
        }).flatten(View::of).filter(gl -> label == null || label.equals(gl.label())).toList();

    bar.complete();

    System.out.println(); // Cosmetic
    observations.add(String.format("%d gold labels loaded.", gls.size()));

    return gls;
  }

  /**
   * Load gold labels from a gzip file.
   *
   * @param file gold labels as JSON objects stored inside a gzip file.
   * @return a list of {@link IGoldLabel}.
   */
  @Deprecated
  static List<IGoldLabel<String>> load(Observations observations, File file, String label) {

    Preconditions.checkNotNull(observations, "observations should not be null");
    Preconditions.checkNotNull(file, "file should not be null");
    Preconditions.checkArgument(file.exists(), "file should exist : %s", file);

    observations.add("Loading gold labels...");

    AsciiProgressBar.IndeterminateProgressBar bar = AsciiProgressBar.createIndeterminate();

    List<IGoldLabel<String>> gls = View.of(file, true).index()
        .filter(e -> !Strings.isNullOrEmpty(e.getValue())).peek(e -> bar.update())
        .map(e -> (IGoldLabel<String>) new GoldLabel(JsonCodec.asObject(e.getValue())))
        .filter(gl -> label == null || label.equals(gl.label())).toList();

    bar.complete();

    System.out.println(); // Cosmetic
    observations.add(String.format("%d gold labels loaded.", gls.size()));

    return gls;
  }

  /**
   * Split a set of gold labels i.e. reference labels into 3 subsets : dev, train and test. The dev
   * subset is made of 25% of the dataset, the train subset is made of 50% of the dataset and the
   * test subset is made of 25% of the dataset.
   * 
   * @param goldLabels gold labels.
   * @param <D> type of the original data points.
   * @param <T> type of the gold labels.
   * @return a list. The first element is the dev dataset, the second element is the train dataset
   *         and the third element is the test dataset.
   */
  static <D, T extends IGoldLabel<D>> List<Set<T>> split(Collection<T> goldLabels) {
    return split(goldLabels, false, 0.25, 0.50);
  }

  /**
   * Split a set of gold labels i.e. reference labels into 3 subsets : dev, train and test.
   *
   * @param goldLabels gold labels.
   * @param keepProportions must be true iif the proportion of TP, TN, FP and FN must be the same in
   *        the dev, train and test subsets.
   * @param <D> type of the original data points.
   * @param <T> type of the gold labels.
   * @return a list. The first element is the dev dataset, the second element is the train dataset
   *         and the third element is the test dataset.
   */
  static <D, T extends IGoldLabel<D>> List<Set<T>> split(Collection<T> goldLabels,
      boolean keepProportions, double devSizeInPercent, double trainSizeInPercent) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(0.0 <= devSizeInPercent && devSizeInPercent <= 1.0,
        "devSizeInPercent must be such as 0.0 <= devSizeInPercent <= 1.0");
    Preconditions.checkArgument(0.0 <= trainSizeInPercent && trainSizeInPercent <= 1.0,
        "trainSizeInPercent must be such as 0.0 <= trainSizeInPercent <= 1.0");
    Preconditions.checkArgument(devSizeInPercent + trainSizeInPercent <= 1.0,
        "devSizeInPercent + trainSizeInPercent must be <= 1.0");

    List<T> gls = Lists.newArrayList(goldLabels);

    Collections.shuffle(gls);

    Set<T> dev = new HashSet<>();
    Set<T> train = new HashSet<>();
    Set<T> test = new HashSet<>();

    if (!keepProportions) {

      int devSize = (int) (gls.size() * devSizeInPercent);
      int trainSize = (int) (gls.size() * trainSizeInPercent);

      dev.addAll(gls.subList(0, devSize));
      train.addAll(gls.subList(devSize, devSize + trainSize));
      test.addAll(gls.subList(devSize + trainSize, gls.size()));
    } else {

      List<T> tps =
          goldLabels.stream().filter(IGoldLabel::isTruePositive).collect(Collectors.toList());
      List<T> tns =
          goldLabels.stream().filter(IGoldLabel::isTrueNegative).collect(Collectors.toList());
      List<T> fps =
          goldLabels.stream().filter(IGoldLabel::isFalsePositive).collect(Collectors.toList());
      List<T> fns =
          goldLabels.stream().filter(IGoldLabel::isFalseNegative).collect(Collectors.toList());

      int devTpSize = (int) (devSizeInPercent * tps.size());
      int devTnSize = (int) (devSizeInPercent * tns.size());
      int devFpSize = (int) (devSizeInPercent * fps.size());
      int devFnSize = (int) (devSizeInPercent * fns.size());

      int trainTpSize = (int) (trainSizeInPercent * tps.size());
      int trainTnSize = (int) (trainSizeInPercent * tns.size());
      int trainFpSize = (int) (trainSizeInPercent * fps.size());
      int trainFnSize = (int) (trainSizeInPercent * fns.size());

      dev.addAll(tps.subList(0, devTpSize));
      dev.addAll(tns.subList(0, devTnSize));
      dev.addAll(fps.subList(0, devFpSize));
      dev.addAll(fns.subList(0, devFnSize));

      train.addAll(tps.subList(devTpSize, devTpSize + trainTpSize));
      train.addAll(tns.subList(devTnSize, devTnSize + trainTnSize));
      train.addAll(fps.subList(devFpSize, devFpSize + trainFpSize));
      train.addAll(fns.subList(devFnSize, devFnSize + trainFnSize));

      test.addAll(tps.subList(devTpSize + trainTpSize, tps.size()));
      test.addAll(tns.subList(devTnSize + trainTnSize, tns.size()));
      test.addAll(fps.subList(devFpSize + trainFpSize, fps.size()));
      test.addAll(fns.subList(devFnSize + trainFnSize, fns.size()));
    }

    Preconditions.checkState(dev.size() + train.size() + test.size() == gls.size(),
        "Inconsistent state reached for splits : %s found vs %s expected",
        dev.size() + train.size() + test.size(), gls.size());

    return Lists.newArrayList(dev, train, test);
  }

  /**
   * Build a confusion matrix from a set of gold labels.
   *
   * @param goldLabels gold labels.
   * @param <D> type of the original data points.
   * @param <T> type of the gold labels.
   * @return a {@link ConfusionMatrix}.
   */
  static <D, T extends IGoldLabel<D>> ConfusionMatrix confusionMatrix(List<T> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    ConfusionMatrix matrix = new ConfusionMatrix();

    for (int i = 0; i < goldLabels.size(); i++) {

      T gl = goldLabels.get(i);

      int tp = gl.isTruePositive() ? 1 : 0;
      int tn = gl.isTrueNegative() ? 1 : 0;
      int fp = gl.isFalsePositive() ? 1 : 0;
      int fn = gl.isFalseNegative() ? 1 : 0;

      Preconditions.checkState(tp + tn + fp + fn == 1,
          "Inconsistent state reached for gold label : (%s, %s)", gl.label(), gl.id());

      matrix.addTruePositives(tp);
      matrix.addTrueNegatives(tn);
      matrix.addFalsePositives(fp);
      matrix.addFalseNegatives(fn);
    }
    return matrix;
  }

  /**
   * Get the gold label unique identifier.
   *
   * @return a unique identifier.
   */
  String id();

  /**
   * Get the gold label class.
   *
   * @return the label name.
   */
  String label();

  /**
   * Get the data point associated to this gold label.
   *
   * @return the data point.
   */
  D data();

  /**
   * Check if the gold label is a TP.
   *
   * @return true iif the current gold label is a TP, false otherwise.
   */
  boolean isTruePositive();

  /**
   * Check if the gold label is a FP.
   *
   * @return true iif the current gold label is a FP, false otherwise.
   */
  boolean isFalsePositive();

  /**
   * Check if the gold label is a TN.
   *
   * @return true iif the current gold label is a TN, false otherwise.
   */
  boolean isTrueNegative();

  /**
   * Check if the gold label is a FN.
   *
   * @return true iif the current gold label is a FN, false otherwise.
   */
  boolean isFalseNegative();

  /**
   * If {@code D} is an instance of {@link String}, an optional text snippet which must be a
   * substring of {@link data()}. This text snippet is used by
   * {@link com.computablefacts.morta.docsetlabeler.DocSetLabelerImpl} to boost terms included in
   * it.
   *
   * @return a text snippet.
   */
  default String snippet() {
    return "";
  }

  /**
   * A sanitized version of the string returned by the {@link snippet()} method.
   *
   * @return a sanitized text snippet.
   */
  default String snippetSanitized() {
    return snippet().replaceAll("(?s)[\\p{Zs}\\n\\r\\t]+", " ");
  }

  /**
   * Output the Gold Label in <a href="https://prodi.gy/">Prodigy</a> data format for
   * <a href="https://prodi.gy/docs/text-classification">text classification</a> or
   * <a href="https://prodi.gy/docs/span-categorization">span categorization</a>.
   *
   * @return an {@link AnnotatedText}.
   */
  default Optional<AnnotatedText> annotatedText() {

    if (!isTruePositive() && !isTrueNegative() && !isFalsePositive() && !isFalseNegative()) {
      return Optional.empty();
    }
    if (!(data() instanceof String)) {
      return Optional.empty();
    }

    boolean accept = isTruePositive() || isFalseNegative();
    String data = (String) data();

    if (Strings.isNullOrEmpty(snippet()) || !data.contains(snippet())) {
      Meta meta = new Meta(id(), label(), accept ? "accept" : "reject");
      return Optional.of(new AnnotatedText(meta, data));
    }

    int beginSpan = data.indexOf(snippet());
    int endSpan = beginSpan + snippet().length();

    @Var
    int firstSpanId = -1;
    @Var
    int lastSpanId = -1;

    List<Token> tokens = new ArrayList<>();
    Matcher tokenizer =
        Pattern.compile("[^\\p{Zs}\\n]+", Pattern.DOTALL | Pattern.MULTILINE).matcher(data);

    while (tokenizer.find()) {

      String text = tokenizer.group();
      int beginToken = tokenizer.start();
      int endToken = tokenizer.end();

      if (beginToken <= beginSpan) {
        firstSpanId = tokens.size();
      }
      if (endToken <= endSpan) {
        lastSpanId = tokens.size();
      }

      tokens.add(new Token(text, beginToken, endToken, tokens.size(), true));
    }

    Span span = new Span(beginSpan, endSpan, firstSpanId, lastSpanId, label());
    Meta meta = new Meta(id(), label(), accept ? "accept" : "reject", snippet());

    return Optional.of(new AnnotatedText(meta, data, tokens, Lists.newArrayList(span)));
  }
}
