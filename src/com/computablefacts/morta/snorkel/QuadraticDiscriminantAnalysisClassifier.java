package com.computablefacts.morta.snorkel;

import java.util.Set;

import com.google.errorprone.annotations.CheckReturnValue;

import smile.classification.Classifier;
import smile.classification.QDA;

/**
 * Train a quadratic discriminant analysis classifier.
 */
@CheckReturnValue
final public class QuadraticDiscriminantAnalysisClassifier extends AbstractClassifier {

  public QuadraticDiscriminantAnalysisClassifier() {}

  @Override
  protected Classifier<double[]> train(Set<Integer> classes, double[][] instances, int[] labels) {
    return QDA.fit(instances, labels);
  }
}
