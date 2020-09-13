package com.computablefacts.morta;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public interface IGoldLabel<D> {

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
    return split(goldLabels, 0.25, 0.50);
  }

  /**
   * Split a set of gold labels i.e. reference labels into 3 subsets : dev, train and test.
   *
   * @param goldLabels gold labels.
   * @param <D> type of the original data points.
   * @param <T> type of the gold labels.
   * @return a list. The first element is the dev dataset, the second element is the train dataset
   *         and the third element is the test dataset.
   */
  static <D, T extends IGoldLabel<D>> List<Set<T>> split(Collection<T> goldLabels,
      double devSizeInPercent, double trainSizeInPercent) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(0.0 <= devSizeInPercent && devSizeInPercent <= 1.0,
        "devSizeInPercent must be such as 0.0 <= devSizeInPercent <= 1.0");
    Preconditions.checkArgument(0.0 <= trainSizeInPercent && trainSizeInPercent <= 1.0,
        "trainSizeInPercent must be such as 0.0 <= trainSizeInPercent <= 1.0");
    Preconditions.checkArgument(devSizeInPercent + trainSizeInPercent <= 1.0,
        "devSizeInPercent + trainSizeInPercent must be <= 1.0");

    List<T> gls = Lists.newArrayList(goldLabels);

    Collections.shuffle(gls);

    int devSize = (int) (gls.size() * devSizeInPercent);
    int trainSize = (int) (gls.size() * trainSizeInPercent);

    Set<T> dev = new HashSet<>(gls.subList(0, devSize));
    Set<T> train = new HashSet<>(gls.subList(devSize, devSize + trainSize));
    Set<T> test = new HashSet<>(gls.subList(devSize + trainSize, gls.size()));

    Preconditions.checkState(dev.size() + train.size() + test.size() == gls.size());

    return Lists.newArrayList(dev, train, test);
  }

  /**
   * Build a confusion matrix from a set of gold labels.
   *
   * @param label considered label.
   * @param goldLabels gold labels.
   * @param <D> type of the original data points.
   * @param <T> type of the gold labels.
   * @return a {@link ConfusionMatrix}.
   */
  static <D, T extends IGoldLabel<D>> ConfusionMatrix confusionMatrix(String label,
      Set<T> goldLabels) {

    Preconditions.checkArgument(!Strings.isNullOrEmpty(label),
        "label should neither be null nor empty");
    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    List<T> gls =
        goldLabels.stream().filter(gl -> gl.label().equals(label)).collect(Collectors.toList());
    ConfusionMatrix matrix = new ConfusionMatrix(label);

    for (int i = 0; i < gls.size(); i++) {

      T gl = gls.get(i);

      if (label.equals(gl.label())) {

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
}
