package com.computablefacts.morta.snorkel;

import java.util.Set;

import com.google.errorprone.annotations.CheckReturnValue;

import smile.classification.Classifier;

/**
 * Train a logistic regression model.
 */
@CheckReturnValue
final public class LogisticRegressionClassifier extends AbstractClassifier {

  public LogisticRegressionClassifier() {}

  @Override
  protected Classifier<double[]> train(Set<Integer> classes, double[][] instances, int[] labels) {
    return classes.size() == 2 ? smile.classification.LogisticRegression.binomial(instances, labels)
        : smile.classification.LogisticRegression.multinomial(instances, labels);
  }
}
