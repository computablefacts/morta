package com.computablefacts.morta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public interface IGoldLabel<D> {

  // result[0] = dev (25% of the dataset)
  // result[1] = train (50% of the dataset)
  // result[2] = test (25% of the dataset)
  static <D, T extends IGoldLabel<D>> List<Set<T>> split(Set<T> goldLabels) {
    return split(goldLabels, 0.25, 0.50);
  }

  // result[0] = dev
  // result[1] = train
  // result[2] = test
  static <D, T extends IGoldLabel<D>> List<Set<T>> split(Set<T> goldLabels, double devSizeInPercent,
      double trainSizeInPercent) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(0.0 <= devSizeInPercent && devSizeInPercent <= 1.0,
        "devSizeInPercent must be such as 0.0 <= devSizeInPercent <= 1.0");
    Preconditions.checkArgument(0.0 <= trainSizeInPercent && trainSizeInPercent <= 1.0,
        "trainSizeInPercent must be such as 0.0 <= trainSizeInPercent <= 1.0");
    Preconditions.checkArgument(devSizeInPercent + trainSizeInPercent <= 1.0,
        "devSizeInPercent + trainSizeInPercent must be <= 1.0");

    List<T> gls = new ArrayList<>(goldLabels);

    Collections.shuffle(gls);

    int devSize = (int) (gls.size() * devSizeInPercent);
    int trainSize = (int) (gls.size() * trainSizeInPercent);

    Set<T> dev = new HashSet<>(gls.subList(0, devSize));
    Set<T> train = new HashSet<>(gls.subList(devSize, devSize + trainSize));
    Set<T> test = new HashSet<>(gls.subList(devSize + trainSize, gls.size()));

    Preconditions.checkState(dev.size() + train.size() + test.size() == gls.size());

    return Lists.newArrayList(dev, train, test);
  }

  static <D, T extends IGoldLabel<D>> ConfusionMatrix confusionMatrix(String label,
      Set<T> goldLabels) {

    Preconditions.checkArgument(!Strings.isNullOrEmpty(label),
        "label should neither be null nor empty");
    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    List<T> gls = new ArrayList<>(goldLabels);
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
