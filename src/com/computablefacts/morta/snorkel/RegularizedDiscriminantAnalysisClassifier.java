package com.computablefacts.morta.snorkel;

import java.util.Set;

import com.google.errorprone.annotations.CheckReturnValue;

import smile.classification.Classifier;
import smile.classification.RDA;

/**
 * Train a regularized discriminant analysis classifier.
 */
@CheckReturnValue
final public class RegularizedDiscriminantAnalysisClassifier extends AbstractClassifier {

  public RegularizedDiscriminantAnalysisClassifier() {}

  @Override
  protected Classifier<double[]> train(Set<Integer> classes, double[][] instances, int[] labels) {
    return RDA.fit(instances, labels);
  }
}
