package com.computablefacts.morta.classifiers;

import java.util.Properties;
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

    Properties properties = new Properties();
    properties.setProperty("smile.logit.max.iterations", "1000");

    return classes.size() == 2
        ? smile.classification.LogisticRegression.binomial(instances, labels, properties)
        : smile.classification.LogisticRegression.multinomial(instances, labels, properties);
  }
}
