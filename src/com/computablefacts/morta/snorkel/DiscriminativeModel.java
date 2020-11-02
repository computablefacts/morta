package com.computablefacts.morta.snorkel;

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
   * Train a logistic regression model.
   *
   * @param instances a list of feature vectors. There is one feature vector for each data point.
   * @param labels a list of output labels. There is one label associated to each feature vector.
   * @return a {@link LogisticRegression} model.
   */
  public static LogisticRegression trainLogisticRegression(List<FeatureVector<Double>> instances,
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
