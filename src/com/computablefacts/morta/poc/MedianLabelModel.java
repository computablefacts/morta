package com.computablefacts.morta.poc;

import static com.computablefacts.morta.snorkel.ILabelingFunction.ABSTAIN;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import com.computablefacts.morta.Pipeline;
import com.computablefacts.morta.snorkel.AbstractLabelModel;
import com.computablefacts.morta.snorkel.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.ILabelingFunction;
import com.computablefacts.morta.snorkel.Summary;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;

import smile.stat.hypothesis.CorTest;

/**
 * This model is especially good when all gold labels share the same label. Each labeling function
 * is weighted according to the label class : true positive, false positive, true negative or false
 * negative. Each labeling function MUST output a value in {ABSTAIN, LABEL_OK, LABEL_KO}.
 * 
 * @param <T> data type.
 */
@CheckReturnValue
final public class MedianLabelModel<T> extends AbstractLabelModel<T> {

  public static final int LABEL_KO = 0;
  public static final int LABEL_OK = 1;

  private List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries_;
  private double thresholdOk_;
  private double thresholdKo_;

  public MedianLabelModel(List<AbstractLabelingFunction<T>> lfs,
      List<? extends IGoldLabel<T>> goldLabels) {

    super(labelingFunctionNamesPrivate(lfs), labelingFunctionLabelsPrivate(), lfs, goldLabels);

    Preconditions.checkArgument(
        goldLabels.stream().allMatch(gl -> gl.label().equals(goldLabels.get(0).label())),
        "Labels must be all the same");

    fit();
  }

  public MedianLabelModel(List<AbstractLabelingFunction<T>> lfs,
      List<? extends IGoldLabel<T>> goldLabels,
      List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries,
      double thresholdOk, double thresholdKo) {

    super(labelingFunctionNamesPrivate(lfs), labelingFunctionLabelsPrivate(), lfs, goldLabels);

    Preconditions.checkArgument(
        goldLabels.stream().allMatch(gl -> gl.label().equals(goldLabels.get(0).label())),
        "Labels must be all the same");
    Preconditions.checkNotNull(lfSummaries, "lfSummaries should not be null");
    Preconditions.checkArgument(thresholdOk >= 0.0, "threshold OK must be >= 0");
    Preconditions.checkArgument(thresholdKo >= 0.0, "threshold KO must be >= 0");

    lfSummaries_ = new ArrayList<>(lfSummaries);
    thresholdOk_ = thresholdOk;
    thresholdKo_ = thresholdKo;
  }

  private static <T> Dictionary labelingFunctionNamesPrivate(
      List<? extends AbstractLabelingFunction<T>> lfs) {

    Preconditions.checkNotNull(lfs, "lfs should not be null");

    Dictionary lfNames = new Dictionary();

    for (int i = 0; i < lfs.size(); i++) {
      lfNames.put(lfs.get(i).name(), i);
    }
    return lfNames;
  }

  private static Dictionary labelingFunctionLabelsPrivate() {

    Dictionary lfOutputs = new Dictionary();
    lfOutputs.put("KO", LABEL_KO);
    lfOutputs.put("OK", LABEL_OK);

    return lfOutputs;
  }

  @Override
  public Table<String, String, CorTest> labelingFunctionsCorrelations(
      Summary.eCorrelation correlation) {

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

    return Summary.labelingFunctionsCorrelations(lfNames(), lfLabels(),
        Pipeline
            .on(goldLabels()).transform(IGoldLabel::data).peek(d -> bar
                .update(count.incrementAndGet(), goldLabels().size(), "Computing correlations..."))
            .label(lfs()).collect(),
        correlation);
  }

  @Override
  public Table<String, Summary.eStatus, List<Map.Entry<T, FeatureVector<Integer>>>> explore() {

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

    return Summary.explore(lfNames(), lfLabels(),
        Pipeline.on(goldLabels()).transform(IGoldLabel::data)
            .peek(d -> bar.update(count.incrementAndGet(), goldLabels().size(), "Exploring..."))
            .label(lfs()).collect(),
        Pipeline.on(goldLabels()).transform(this::label).collect());
  }

  @Override
  public List<Summary> summarize() {

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

    return Summary.summarize(lfNames(), lfLabels(),
        Pipeline.on(goldLabels()).transform(IGoldLabel::data)
            .peek(d -> bar.update(count.incrementAndGet(), goldLabels().size(), "Summarizing..."))
            .label(lfs()).collect(),
        Pipeline.on(goldLabels()).transform(this::label).collect());
  }

  @Override
  public void fit() {

    // Map each LF to its summary
    List<Summary> summaries = summarize();

    lfSummaries_ = lfs().stream().map(lf -> {

      Optional<Summary> summary = summaries.stream()
          .filter(s -> s.label().equals(((AbstractLabelingFunction<T>) lf).name())).findFirst();

      Preconditions.checkState(summary.isPresent(),
          "Inconsistent state reached between LF and Summaries");

      return new AbstractMap.SimpleEntry<>(lf, summary.get());
    }).collect(Collectors.toList());

    // Weight each LF
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
    List<? extends IGoldLabel<T>> goldLabels = goldLabels();
    List<Double> averagesOk = new ArrayList<>(goldLabels.size());
    List<Double> averagesKo = new ArrayList<>(goldLabels.size());

    for (int i = 0; i < goldLabels.size(); i++) {

      bar.update(i, goldLabels.size(), "Weighting labeling functions...");

      IGoldLabel<T> goldLabel = goldLabels.get(i);
      T data = goldLabel.data();
      int label = label(goldLabel);

      if (label == LABEL_OK) {
        averagesOk.add(average(lfSummaries_, LABEL_OK, data));
      } else if (label == LABEL_KO) {
        averagesKo.add(average(lfSummaries_, LABEL_KO, data));
      }
      // else {
      // ABSTAIN
      // }
    }

    // Compute threshold (median) above which weighted LF should have output LABEL_OK or LABEL_KO
    thresholdOk_ = median(averagesOk);
    thresholdKo_ = median(averagesKo);
  }

  @Override
  public List<Integer> predict() {
    return goldLabels().stream().map(IGoldLabel::data)
        .map(data -> predict(lfSummaries_, thresholdOk_, thresholdKo_, data))
        .collect(Collectors.toList());
  }

  public List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries() {
    return lfSummaries_;
  }

  protected List<? extends AbstractLabelingFunction<T>> labelingFunctions() {
    return lfs();
  }

  public Dictionary labelingFunctionNames() {
    return labelingFunctionNamesPrivate(labelingFunctions());
  }

  public Dictionary labelingFunctionLabels() {
    return labelingFunctionLabelsPrivate();
  }

  public double thresholdOk() {
    return thresholdOk_;
  }

  public double thresholdKo() {
    return thresholdKo_;
  }

  public ConfusionMatrix confusionMatrix() {

    List<Integer> actual = goldLabels().stream().map(this::label).collect(Collectors.toList());
    List<Integer> predicted = predict();

    ConfusionMatrix matrix = new ConfusionMatrix();
    matrix.addAll(actual, predicted, LABEL_OK, LABEL_KO);

    return matrix;
  }

  /**
   * Returns the binary class i.e. LABEL_OK or LABEL_KO, associated with a gold label.
   *
   * @param goldLabel gold label.
   * @return a binary class.
   */
  private int label(IGoldLabel<T> goldLabel) {

    Preconditions.checkNotNull(goldLabel, "goldLabel should not be null");

    return goldLabel.isTruePositive() || goldLabel.isFalseNegative() ? LABEL_OK : LABEL_KO;
  }

  private double average(
      List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries, int label,
      T data) {

    Preconditions.checkNotNull(lfSummaries, "lfSummaries should not be null");
    Preconditions.checkArgument(label == ABSTAIN || label == LABEL_OK || label == LABEL_KO,
        "unknown label class");
    Preconditions.checkNotNull(data, "data should not be null");

    List<Double> vector = new ArrayList<>();

    // For each LF, compute the percentage of correct matches
    for (Map.Entry<? extends ILabelingFunction<T>, Summary> lfSummary : lfSummaries) {

      ILabelingFunction<T> lf = lfSummary.getKey();
      Summary summary = lfSummary.getValue();

      if (lf.apply(data) == label) {
        vector.add(summary.correct() / (double) (summary.correct() + summary.incorrect()));
      } else {
        vector.add(0.0);
      }
    }

    // For each LF, average the percentage of correct matches above which "label" should be
    // triggered
    return vector.stream().mapToDouble(d -> d).average().orElse(0.0);
  }

  private double median(List<Double> list) {

    Preconditions.checkNotNull(list, "list should not be null");

    int size = list.size();
    DoubleStream averagesSorted = list.stream().mapToDouble(a -> a).sorted();

    return size % 2 == 0 ? averagesSorted.skip(size / 2 - 1).limit(2).average().orElse(0.0)
        : averagesSorted.skip(size / 2).findFirst().orElse(0.0);
  }

  private int predict(List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries,
      double thresholdOk, double thresholdKo, T data) {

    Preconditions.checkNotNull(lfSummaries, "lfSummaries should not be null");
    Preconditions.checkArgument(thresholdOk >= 0.0, "threshold OK must be >= 0");
    Preconditions.checkArgument(thresholdKo >= 0.0, "threshold KO must be >= 0");

    List<Double> vectorOk = new ArrayList<>();
    List<Double> vectorKo = new ArrayList<>();

    for (Map.Entry<? extends ILabelingFunction<T>, Summary> lfSummary : lfSummaries) {

      ILabelingFunction<T> lf = lfSummary.getKey();
      Summary summary = lfSummary.getValue();
      int label = lf.apply(data);

      if (label == LABEL_OK) {
        vectorOk.add(summary.correct() / (double) (summary.correct() + summary.incorrect()));
      } else if (label == LABEL_KO) {
        vectorKo.add(summary.correct() / (double) (summary.correct() + summary.incorrect()));
      }
      // else {
      // ABSTAIN
      // }
    }

    double averageOk = vectorOk.stream().mapToDouble(d -> d).average().orElse(0.0);
    double averageKo = vectorKo.stream().mapToDouble(d -> d).average().orElse(0.0);

    if (averageOk >= thresholdOk && averageKo < thresholdKo) {
      return LABEL_OK;
    }
    if (averageOk < thresholdOk && averageKo >= thresholdKo) {
      return LABEL_KO;
    }
    return ABSTAIN;
  }
}
