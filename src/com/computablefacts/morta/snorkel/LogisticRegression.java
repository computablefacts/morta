package com.computablefacts.morta.snorkel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.computablefacts.morta.Pipeline;
import com.computablefacts.morta.poc.MedianLabelModel;
import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class LogisticRegression {

  private LogisticRegression() {}

  /**
   * Train a logistic regression model.
   *
   * @param instances a list of feature vectors. There is one feature vector for each data point.
   * @param labels a list of output labels. There is one label associated to each feature vector.
   * @return a {@link smile.classification.LogisticRegression} model.
   */
  public static smile.classification.LogisticRegression train(List<FeatureVector<Double>> instances,
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
    return classes.size() == 2 ? smile.classification.LogisticRegression.binomial(insts, lbls)
        : smile.classification.LogisticRegression.multinomial(insts, lbls);
  }

  public static List<Integer> predict(smile.classification.LogisticRegression logisticRegression,
      List<FeatureVector<Double>> instances) {

    Preconditions.checkNotNull(logisticRegression, "logisticRegression should not be null");
    Preconditions.checkNotNull(instances, "instances should not be null");

    return Pipeline.on(instances)
        .transform(vector -> logisticRegression.predict(vector.toDoubleArray())).collect();
  }
}
