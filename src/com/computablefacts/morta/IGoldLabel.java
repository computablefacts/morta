package com.computablefacts.morta;

import java.util.*;
import java.util.stream.Collectors;

import com.computablefacts.asterix.ConfusionMatrix;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public interface IGoldLabel<D> {

  @Deprecated
  String SANITIZE_SNIPPET = "(?s)[\\p{Zs}\\n\\r\\t]+";

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
   * Returns the current gold label as a {@link Map}.
   *
   * @return a {@link Map}.
   */
  default Map<String, Object> asMap() {

    Map<String, Object> map = new HashMap<>();
    map.put("id", id());
    map.put("label", label());
    map.put("data", data());
    map.put("is_true_positive", isTruePositive());
    map.put("is_false_positive", isFalsePositive());
    map.put("is_true_negative", isTrueNegative());
    map.put("is_false_negative", isFalseNegative());

    return map;
  }
}
