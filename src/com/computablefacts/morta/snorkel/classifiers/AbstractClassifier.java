package com.computablefacts.morta.snorkel.classifiers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.morta.snorkel.Pipeline;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

import smile.classification.Classifier;

@CheckReturnValue
public abstract class AbstractClassifier {

  private Classifier<double[]> classifier_;
  private double mcc_;

  /**
   * Set the Matthews correlation coefficient associated with this label model.
   *
   * @param mcc Matthews correlation coefficient.
   */
  public void mcc(double mcc) {
    mcc_ = mcc;
  }

  /**
   * Get the Matthews correlation coefficient associated with this label model.
   */
  public double mcc() {
    return mcc_;
  }

  /**
   * Predict output using a previously trained classifier.
   *
   * @param instances a list of feature vectors. There is one feature vector for each data point.
   * @return a prediction.
   */
  public List<Integer> predict(List<FeatureVector<Double>> instances) {

    Preconditions.checkNotNull(instances, "instances should not be null");
    Preconditions.checkState(classifier_ != null,
        "classifier should be trained before calling predict(...)");

    return Pipeline.on(instances).transform(this::predict).collect();
  }

  /**
   * Predict output using a previously trained classifier.
   *
   * @param vector a single feature vector.
   * @return a prediction.
   */
  public Integer predict(FeatureVector<Double> vector) {

    Preconditions.checkNotNull(vector, "vector should not be null");
    Preconditions.checkState(classifier_ != null,
        "classifier should be trained before calling predict(...)");

    return classifier_.predict(vector.toDoubleArray());
  }

  /**
   * Train a classifier.
   *
   * @param instances a list of feature vectors. There is one feature vector for each data point.
   * @param labels a list of output labels. There is one label associated to each feature vector.
   */
  public void train(List<FeatureVector<Double>> instances, List<Integer> labels) {

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

    classifier_ = train(classes, insts, lbls);
  }

  protected abstract Classifier<double[]> train(Set<Integer> classes, double[][] instances,
      int[] labels);
}
