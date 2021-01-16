package com.computablefacts.morta.snorkel.classifiers;

import java.util.Set;

import com.google.errorprone.annotations.CheckReturnValue;

import smile.classification.Classifier;
import smile.classification.FLD;

/**
 * Train a Fisher's linear discriminant classifier.
 */
@CheckReturnValue
final public class FisherLinearDiscriminantClassifier extends AbstractClassifier {

  public FisherLinearDiscriminantClassifier() {}

  @Override
  protected Classifier<double[]> train(Set<Integer> classes, double[][] instances, int[] labels) {
    return FLD.fit(instances, labels);
  }
}
