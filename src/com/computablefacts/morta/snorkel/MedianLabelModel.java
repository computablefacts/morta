package com.computablefacts.morta.snorkel;

import static com.computablefacts.morta.snorkel.ILabelingFunction.ABSTAIN;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import com.computablefacts.morta.Pipeline;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

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

  private double thresholdOk_;
  private double thresholdKo_;

  public MedianLabelModel(List<AbstractLabelingFunction<T>> lfs,
      List<? extends IGoldLabel<T>> goldLabels) {

    super(labelingFunctionNames(lfs), labelingFunctionLabels(), lfs, goldLabels);

    Preconditions.checkArgument(
        goldLabels.stream().allMatch(gl -> gl.label().equals(goldLabels.get(0).label())),
        "Labels must be all the same");
  }

  public static <T> Dictionary labelingFunctionNames(List<AbstractLabelingFunction<T>> lfs) {

    Preconditions.checkNotNull(lfs, "lfs should not be null");

    Dictionary lfNames = new Dictionary();

    for (int i = 0; i < lfs.size(); i++) {
      lfNames.put(lfs.get(i).name(), i);
    }
    return lfNames;
  }

  public static Dictionary labelingFunctionLabels() {

    Dictionary lfOutputs = new Dictionary();
    lfOutputs.put("KO", LABEL_KO);
    lfOutputs.put("OK", LABEL_OK);

    return lfOutputs;
  }

  @Override
  public Table<String, String, CorTest> labelingFunctionsCorrelations(
      Summary.eCorrelation correlation) {
    return Summary.labelingFunctionsCorrelations(lfNames(), lfLabels(),
        Pipeline.on(goldLabels()).transform(IGoldLabel::data).label(lfs()).collect(), correlation);
  }

  @Override
  public Table<String, Summary.eStatus, List<Map.Entry<T, FeatureVector<Integer>>>> explore() {
    return Summary.explore(lfNames(), lfLabels(),
        Pipeline.on(goldLabels()).transform(IGoldLabel::data).label(lfs()).collect(),
        Pipeline.on(goldLabels()).transform(this::label).collect());
  }

  @Override
  public List<Summary> summarize() {
    return Summary.summarize(lfNames(), lfLabels(),
        Pipeline.on(goldLabels()).transform(IGoldLabel::data).label(lfs()).collect(),
        Pipeline.on(goldLabels()).transform(this::label).collect());
  }

  @Override
  public List<Integer> predict() {

    // Map each LF to its summary
    List<Summary> summaries = summarize();

    List<Map.Entry<? extends ILabelingFunction<T>, Summary>> lfSummaries =
        lfs().stream().map(lf -> {

          Optional<Summary> summary = summaries.stream()
              .filter(s -> s.label().equals(((AbstractLabelingFunction<T>) lf).name())).findFirst();

          Preconditions.checkState(summary.isPresent(),
              "Inconsistent state reached between LF and Summaries");

          return new AbstractMap.SimpleEntry<>(lf, summary.get());
        }).collect(Collectors.toList());

    // Weight each LF
    List<? extends IGoldLabel<T>> goldLabels = goldLabels();
    List<Double> averagesOk = new ArrayList<>(goldLabels.size());
    List<Double> averagesKo = new ArrayList<>(goldLabels.size());

    for (int i = 0; i < goldLabels.size(); i++) {

      IGoldLabel<T> goldLabel = goldLabels.get(i);
      T data = goldLabel.data();
      int label = label(goldLabel);

      if (label == LABEL_OK) {

        List<Double> vector = new ArrayList<>();

        // For each LF, compute the percentage of correct LABEL_OK/LABEL_KO matches
        for (Map.Entry<? extends ILabelingFunction<T>, Summary> lfSummary : lfSummaries) {

          ILabelingFunction<T> lf = lfSummary.getKey();
          Summary summary = lfSummary.getValue();

          if (lf.apply(data) == LABEL_OK) {
            vector.add(summary.correct() / (double) (summary.correct() + summary.incorrect()));
          } else {
            vector.add(0.0);
          }
        }

        // For each LF, average the percentage of correct LABEL_OK/LABEL_KO matches
        double average = vector.stream().mapToDouble(d -> d).average().orElse(0.0);
        averagesOk.add(average);
      } else if (label == LABEL_KO) {

        List<Double> vector = new ArrayList<>();

        // For each LF, compute the percentage of correct LABEL_OK/LABEL_KO matches
        for (Map.Entry<? extends ILabelingFunction<T>, Summary> lfSummary : lfSummaries) {

          ILabelingFunction<T> lf = lfSummary.getKey();
          Summary summary = lfSummary.getValue();

          if (lf.apply(data) == LABEL_KO) {
            vector.add(summary.correct() / (double) (summary.correct() + summary.incorrect()));
          } else {
            vector.add(0.0);
          }
        }

        // For each LF, average the percentage of correct LABEL_OK/LABEL_KO matches
        double average = vector.stream().mapToDouble(d -> d).average().orElse(0.0);
        averagesKo.add(average);
      }
      // else {
      // ABSTAIN
      // }
    }

    // Compute threshold (median) above (resp. under) which a weighted vector should have output
    // LABEL_OK (resp. LABEL_KO)
    @Var
    int size = averagesOk.size();
    @Var
    DoubleStream averagesSorted = averagesOk.stream().mapToDouble(a -> a).sorted();

    thresholdOk_ = size % 2 == 0 ? averagesSorted.skip(size / 2 - 1).limit(2).average().orElse(0.0)
        : averagesSorted.skip(size / 2).findFirst().orElse(0.0);

    size = averagesKo.size();
    averagesSorted = averagesKo.stream().mapToDouble(a -> a).sorted();

    thresholdKo_ = size % 2 == 0 ? averagesSorted.skip(size / 2 - 1).limit(2).average().orElse(0.0)
        : averagesSorted.skip(size / 2).findFirst().orElse(0.0);

    // Make predictions!
    return goldLabels().stream().map(IGoldLabel::data).map(data -> {

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

      if (averageOk >= thresholdOk_ && averageKo < thresholdKo_) {
        return LABEL_OK;
      }
      if (averageOk < thresholdOk_ && averageKo >= thresholdKo_) {
        return LABEL_KO;
      }
      return ABSTAIN;
    }).collect(Collectors.toList());
  }

  /**
   * Returns the binary class i.e. LABEL_OK or LABEL_KO, associated with a gold label.
   *
   * @param goldLabel gold label.
   * @return a binary class.
   */
  protected int label(IGoldLabel<T> goldLabel) {

    Preconditions.checkNotNull(goldLabel, "goldLabel should not be null");

    return goldLabel.isTruePositive() || goldLabel.isFalseNegative() ? LABEL_OK : LABEL_KO;
  }
}
