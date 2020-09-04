package com.computablefacts.morta;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

import smile.classification.LogisticRegression;

@CheckReturnValue
final public class DiscriminativeModel {

  private DiscriminativeModel() {}

  /**
   * Train a discriminative model from a generative one (majority vote).
   *
   * @param lfNames mapping of the labeling function names to integers. Each integer represents the
   *        position of the labeling function in the lfs list.
   * @param lfLabels mapping of the labeling function outputs, i.e. labels, to integers. Each
   *        integer represents a machine-friendly version of a human-readable label.
   * @param lfs labeling functions.
   * @param dataset data points.
   * @param transform transform each data point to a {@link FeatureVector<Double>}.
   * @param <D> type of the original data points.
   * @return a {@link LogisticRegression} model.
   */
  public static <D> LogisticRegression trainLogisticRegression(Dictionary lfNames,
      Dictionary lfLabels, List<LabelingFunction<D>> lfs, Collection<D> dataset,
      TransformationFunction<D, FeatureVector<Double>> transform) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(lfLabels, "lfLabels should not be null");
    Preconditions.checkNotNull(lfs, "lfs should not be null");
    Preconditions.checkNotNull(dataset, "dataset should not be null");
    Preconditions.checkNotNull(transform, "transform should not be null");

    List<FeatureVector<Double>> instances = Pipeline.on(dataset).transform(transform).collect();
    List<Integer> predictions = Pipeline.on(dataset).predictions(lfNames, lfLabels, lfs,
        MajorityLabelModel.eTieBreakPolicy.RANDOM);

    Preconditions.checkArgument(predictions.size() == instances.size(),
        "Mismatch between the number of predictions and the number of data points : %s vs %s",
        predictions.size(), instances.size());

    return logisticRegression(instances, predictions);
  }

  public static LogisticRegression logisticRegression(List<FeatureVector<Double>> instances,
      List<Integer> labels) {

    Preconditions.checkNotNull(instances, "instances should not be null");
    Preconditions.checkNotNull(labels, "labels should not be null");
    Preconditions.checkArgument(instances.size() == labels.size(),
        "Invalid number of labels : %s found vs %s expected", labels.size(), instances.size());

    Set<Integer> classes = new HashSet<>();
    int[] lbls = new int[labels.size()];

    for (int i = 0; i < labels.size(); i++) {
      lbls[i] = labels.get(i);
      classes.add(labels.get(i));
    }

    Preconditions.checkState(classes.size() > 1, "The number of distinct labels must be > 1");

    double[][] insts = new double[instances.size()][instances.get(0).size()];

    for (int i = 0; i < instances.size(); i++) {
      for (int j = 0; j < instances.get(i).size(); j++) {
        insts[i][j] = instances.get(i).get(j);
      }
    }
    return classes.size() == 2 ? LogisticRegression.binomial(insts, lbls)
        : LogisticRegression.multinomial(insts, lbls);
  }
}
