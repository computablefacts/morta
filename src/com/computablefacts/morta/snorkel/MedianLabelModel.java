package com.computablefacts.morta.snorkel;

import static com.computablefacts.morta.snorkel.ILabelingFunction.ABSTAIN;
import static com.computablefacts.morta.snorkel.ILabelingFunction.KO;
import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.computablefacts.morta.Pipeline;
import com.computablefacts.morta.snorkel.AbstractLabelModel;
import com.computablefacts.nona.Generated;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.common.math.Quantiles;
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

  private List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries_;
  private double[] lfMediansOk_;
  private double[] lfMinsOk_;
  private double medianOk_;
  private double minOk_;
  private double[] lfMediansKo_;
  private double[] lfMinsKo_;
  private double medianKo_;
  private double minKo_;

  public MedianLabelModel(MedianLabelModel<T> labelModel) {
    this(labelModel.lfs(), labelModel.lfSummaries());
  }

  public MedianLabelModel(List<? extends AbstractLabelingFunction<T>> lfs) {
    super(lfsNames(lfs), lfsLabels(), lfs);
  }

  private MedianLabelModel(List<? extends AbstractLabelingFunction<T>> lfs,
      List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries) {

    super(lfsNames(lfs), lfsLabels(), lfs);

    Preconditions.checkNotNull(lfSummaries, "lfSummaries should not be null");

    lfSummaries_ = new ArrayList<>(lfSummaries);
  }

  /**
   * Returns the binary class i.e. LABEL_OK or LABEL_KO, associated with a gold label.
   *
   * @param goldLabel gold label.
   * @return a binary class.
   */
  public static <T> int label(IGoldLabel<T> goldLabel) {

    Preconditions.checkNotNull(goldLabel, "goldLabel should not be null");

    return goldLabel.isTruePositive() || goldLabel.isFalseNegative() ? OK : KO;
  }

  private static <T> Dictionary lfsNames(List<? extends AbstractLabelingFunction<T>> lfs) {

    Preconditions.checkNotNull(lfs, "lfs should not be null");

    Dictionary lfNames = new Dictionary();

    for (int i = 0; i < lfs.size(); i++) {
      lfNames.put(lfs.get(i).name(), i);
    }
    return lfNames;
  }

  private static Dictionary lfsLabels() {

    Dictionary lfOutputs = new Dictionary();
    lfOutputs.put("KO", KO);
    lfOutputs.put("OK", OK);

    return lfOutputs;
  }

  @Override
  public Table<String, String, CorTest> labelingFunctionsCorrelations(
      List<? extends IGoldLabel<T>> goldLabels, Summary.eCorrelation correlation) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkNotNull(correlation, "correlation should not be null");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

    return Summary.labelingFunctionsCorrelations(lfNames(), lfLabels(),
        Pipeline.on(goldLabels).transform(IGoldLabel::data)
            .peek(d -> bar.update(count.incrementAndGet(), goldLabels.size(), "Correlating..."))
            .label(lfs()).collect(),
        correlation);
  }

  @Override
  public Table<String, Summary.eStatus, List<Map.Entry<T, FeatureVector<Integer>>>> explore(
      List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

    return Summary.explore(lfNames(), lfLabels(),
        Pipeline.on(goldLabels).transform(IGoldLabel::data)
            .peek(d -> bar.update(count.incrementAndGet(), goldLabels.size(), "Exploring..."))
            .label(lfs()).collect(),
        Pipeline.on(goldLabels).transform(MedianLabelModel::label).collect());
  }

  @Override
  public List<Summary> summarize(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

    return Summary.summarize(lfNames(), lfLabels(),
        Pipeline.on(goldLabels).transform(IGoldLabel::data)
            .peek(d -> bar.update(count.incrementAndGet(), goldLabels.size(), "Summarizing..."))
            .label(lfs()).collect(),
        Pipeline.on(goldLabels).transform(MedianLabelModel::label).collect());
  }

  @Override
  public void fit(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(
        goldLabels.stream().allMatch(gl -> gl.label().equals(goldLabels.get(0).label())),
        "gold labels must be identical");

    // Map each LF to its summary
    List<Summary> summaries = summarize(goldLabels);

    lfSummaries_ = lfs().stream().map(lf -> {

      Optional<Summary> summary =
          summaries.stream().filter(s -> s.label().equals(lf.name())).findFirst();

      Preconditions.checkState(summary.isPresent(),
          "Inconsistent state reached between LF and Summaries");

      return new AbstractMap.SimpleEntry<>(lf, summary.get());
    }).collect(Collectors.toList());

    // Weight each LF
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
    List<List<Double>> ok = new ArrayList<>(goldLabels.size());
    List<List<Double>> ko = new ArrayList<>(goldLabels.size());

    for (int i = 0; i < goldLabels.size(); i++) {

      bar.update(i, goldLabels.size(), "Weighting labeling functions...");

      IGoldLabel<T> goldLabel = goldLabels.get(i);
      T data = goldLabel.data();
      int label = label(goldLabel);

      if (label == OK) {
        ok.add(score(lfSummaries_, label, data));
      } else if (label == KO) {
        ko.add(score(lfSummaries_, label, data));
      } // discard ABSTAIN
    }

    initOkParameters(ok);
    initKoParameters(ko);
  }

  /**
   * Make predictions. All predictions MUST BE in {LABEL_OK, LABEL_KO}.
   *
   * @param goldLabels gold labels.
   * @return output a prediction for each gold label.
   */
  @Override
  public List<Integer> predict(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(
        goldLabels.stream().allMatch(gl -> gl.label().equals(goldLabels.get(0).label())),
        "gold labels must be identical");

    return goldLabels.stream().map(IGoldLabel::data).map(data -> predict(data))
        .collect(Collectors.toList());
  }

  @Generated
  public List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries() {
    return lfSummaries_;
  }

  public List<Map.Entry<T, FeatureVector<Integer>>> vectors(
      List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Pipeline.on(goldLabels).transform(IGoldLabel::data).label(lfs()).collect();
  }

  public List<String> actual(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Pipeline.on(goldLabels).transform(MedianLabelModel::label)
        .transform(pred -> labelingFunctionLabels().label(pred)).collect();
  }

  public List<String> predicted(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return predict(goldLabels).stream()
        .map(pred -> pred == ABSTAIN ? "ABSTAIN" : labelingFunctionLabels().label(pred))
        .collect(Collectors.toList());
  }

  public ConfusionMatrix confusionMatrix(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    List<Integer> actual =
        goldLabels.stream().map(MedianLabelModel::label).collect(Collectors.toList());
    List<Integer> predicted = predict(goldLabels);

    ConfusionMatrix matrix = new ConfusionMatrix();
    matrix.addAll(actual, predicted, OK, KO);

    return matrix;
  }

  /**
   * Evaluate each LF against a given label. Each vector entry is a number in [-1, 1] where 1 means
   * "the LF totally agrees with the given label", 0 means "the LF abstained" and -1 means "the LF
   * totally disagrees with the given label".
   *
   * @param lfSummaries
   * @param label
   * @param data
   * @return vector
   */
  private List<Double> score(
      List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries, int label,
      T data) {

    Preconditions.checkNotNull(lfSummaries, "lfSummaries should not be null");
    Preconditions.checkArgument(label == ABSTAIN || label == OK || label == KO,
        "unknown label class");
    Preconditions.checkNotNull(data, "data should not be null");

    List<Double> vector = new ArrayList<>();

    for (Map.Entry<? extends ILabelingFunction<T>, Summary> lfSummary : lfSummaries) {

      ILabelingFunction<T> lf = lfSummary.getKey();
      Summary summary = lfSummary.getValue();
      int lbl = lf.apply(data);

      if (lbl == label) {
        vector.add(1.0 * (summary.correct() + summary.abstain())
            / (summary.correct() + summary.incorrect() + summary.abstain()));
      } else if (lbl == ABSTAIN) {
        vector.add(0.0);
      } else {
        vector.add(-1.0 * (summary.incorrect() + summary.abstain())
            / (summary.correct() + summary.incorrect() + summary.abstain()));
      }
    }
    return vector;
  }

  private void initOkParameters(List<List<Double>> ok) {

    Preconditions.checkNotNull(ok, "ok should not be null");

    List<Double>[] weights = new List[lfSummaries_.size()]; // transpose scores

    for (int k = 0; k < lfSummaries_.size(); k++) {
      weights[k] = new ArrayList<>();
    }

    for (int i = 0; i < ok.size(); i++) {
      for (int k = 0; k < ok.get(i).size(); k++) {
        weights[k].add(ok.get(i).get(k));
      }
    }

    // Compute thresholds
    lfMediansOk_ = new double[lfSummaries_.size()];
    lfMinsOk_ = new double[lfSummaries_.size()];

    for (int k = 0; k < lfSummaries_.size(); k++) {
      lfMediansOk_[k] = Quantiles.median().compute(weights[k]);
      lfMinsOk_[k] = weights[k].stream().mapToDouble(d -> d).min().orElse(Double.MAX_VALUE);
    }

    medianOk_ = Quantiles.median().compute(
        ok.stream().map(s -> s.stream().mapToDouble(v -> v).sum()).collect(Collectors.toList()));

    minOk_ = ok.stream().mapToDouble(s -> s.stream().mapToDouble(v -> v).sum()).min()
        .orElse(Double.MAX_VALUE);
  }

  private void initKoParameters(List<List<Double>> ko) {

    Preconditions.checkNotNull(ko, "ok should not be null");

    List<Double>[] weights = new List[lfSummaries_.size()]; // transpose scores

    for (int k = 0; k < lfSummaries_.size(); k++) {
      weights[k] = new ArrayList<>();
    }

    for (int i = 0; i < ko.size(); i++) {
      for (int k = 0; k < ko.get(i).size(); k++) {
        weights[k].add(ko.get(i).get(k));
      }
    }

    // Compute thresholds
    lfMediansKo_ = new double[lfSummaries_.size()];
    lfMinsKo_ = new double[lfSummaries_.size()];

    for (int k = 0; k < lfSummaries_.size(); k++) {
      lfMediansKo_[k] = Quantiles.median().compute(weights[k]);
      lfMinsKo_[k] = weights[k].stream().mapToDouble(d -> d).min().orElse(Double.MAX_VALUE);
    }

    medianKo_ = Quantiles.median().compute(
        ko.stream().map(s -> s.stream().mapToDouble(v -> v).sum()).collect(Collectors.toList()));

    minKo_ = ko.stream().mapToDouble(s -> s.stream().mapToDouble(v -> v).sum()).min()
        .orElse(Double.MAX_VALUE);
  }

  private int predict(T data) {

    Preconditions.checkNotNull(data, "data should not be null");

    List<Double> ok = score(lfSummaries_, OK, data);
    List<Double> ko = score(lfSummaries_, KO, data);

    if (outputOkLabel(ok) && !outputKoLabel(ko)) {
      return OK;
    }
    return KO;
  }

  private boolean outputOkLabel(List<Double> ok) {

    Preconditions.checkNotNull(ok, "ok should not be null");
    Preconditions.checkState(ok.size() == lfSummaries_.size(),
        "wrong vector length : %s expected vs %s found", ok.size(), lfSummaries_.size());

    double sum = ok.stream().mapToDouble(v -> v).sum();

    if (sum < minOk_ || sum < medianOk_) {
      return false;
    }

    for (int k = 0; k < ok.size(); k++) {
      if (ok.get(k) < lfMinsOk_[k] || ok.get(k) < lfMediansOk_[k]) {
        return false;
      }
    }
    return true;
  }

  private boolean outputKoLabel(List<Double> ko) {

    Preconditions.checkNotNull(ko, "ko should not be null");
    Preconditions.checkState(ko.size() == lfSummaries_.size(),
        "wrong vector length : %s expected vs %s found", ko.size(), lfSummaries_.size());

    double sum = ko.stream().mapToDouble(v -> v).sum();

    if (sum < minKo_ || sum < medianKo_) {
      return false;
    }

    for (int k = 0; k < ko.size(); k++) {
      if (ko.get(k) < lfMinsKo_[k] || ko.get(k) < lfMediansKo_[k]) {
        return false;
      }
    }
    return true;
  }
}
