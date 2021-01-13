package com.computablefacts.morta.snorkel;

import java.util.Set;

import com.google.errorprone.annotations.CheckReturnValue;

import smile.classification.Classifier;
import smile.classification.LDA;

/**
 * Train a linear discriminant classifier.
 */
@CheckReturnValue
final public class LinearDiscriminantAnalysisClassifier extends AbstractClassifier {

  public LinearDiscriminantAnalysisClassifier() {}

  @Override
  protected Classifier<double[]> train(Set<Integer> classes, double[][] instances, int[] labels) {
    return LDA.fit(instances, labels);
  }
}
