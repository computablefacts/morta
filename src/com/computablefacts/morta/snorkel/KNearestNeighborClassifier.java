package com.computablefacts.morta.snorkel;

import java.util.Set;

import com.google.errorprone.annotations.CheckReturnValue;

import smile.classification.Classifier;
import smile.classification.KNN;

/**
 * Train a K-Nearest Neighbor classifier.
 */
@CheckReturnValue
final public class KNearestNeighborClassifier extends AbstractClassifier {

  public KNearestNeighborClassifier() {}

  @Override
  protected Classifier<double[]> train(Set<Integer> classes, double[][] instances, int[] labels) {
    return KNN.fit(instances, labels);
  }
}
