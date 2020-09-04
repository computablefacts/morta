package com.computablefacts.morta;

import static com.computablefacts.morta.LabelingFunction.ABSTAIN;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

@CheckReturnValue
final public class MajorityLabelModel {

  private MajorityLabelModel() {}

  public static List<FeatureVector<Double>> probabilities(Dictionary lfNames, Dictionary lfLabels,
      List<FeatureVector<Integer>> instances) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(lfLabels, "lfLabels should not be null");
    Preconditions.checkNotNull(instances, "instances should not be null");

    int nbLabelingFunctions = lfNames.size();
    int cardinality = lfLabels.size();

    // instances[n][m] with n = instances.size() and m = the number of distinct labeling functions
    // yp[n][k] with n = instances.size() and k = the number of distinct labels i.e. the cardinality
    List<FeatureVector<Double>> yp = new ArrayList<>(instances.size());

    for (int i = 0; i < instances.size(); i++) {

      FeatureVector<Integer> featureVector = instances.get(i);

      Preconditions.checkState(nbLabelingFunctions == featureVector.size(),
          "Invalid feature vector length : %s found vs %s expected", featureVector.size(),
          nbLabelingFunctions);

      // Count how many times each label is outputted across all labeling functions.
      FeatureVector<Integer> counts = new FeatureVector<>(cardinality, 0);

      for (int m = 0; m < featureVector.size(); m++) {

        // Get the label computed by each labeling function. Each label is mapped to a number
        // between 0 and k-1.
        int label = featureVector.get(m);

        if (label > ABSTAIN) {
          counts.set(label, counts.get(label) + 1);
        }
      }

      // Find the maximum number of votes a single label can get
      @Var
      int max = 0;

      for (int k = 0; k < counts.size(); k++) {
        if (counts.get(k) > max) {
          max = counts.get(k);
        }
      }

      // Output a vector where each label that gets the maximum number of votes is set to 1 and 0
      // otherwise
      FeatureVector<Double> ypi = new FeatureVector<>(cardinality, 0.0);

      for (int k = 0; k < counts.size(); k++) {
        if (counts.get(k) == max) {
          ypi.set(k, 1.0);
        }
      }

      yp.add(ypi);
    }

    // For each instance, compute the probability being associated with each label
    for (FeatureVector<Double> ypi : yp) {

      @Var
      double sum = 0;

      for (int k = 0; k < ypi.size(); k++) {
        sum += ypi.get(k);
      }

      for (int k = 0; k < ypi.size(); k++) {
        ypi.set(k, ypi.get(k) / sum);
      }
    }
    return yp;
  }

  public static List<Integer> predictions(Dictionary lfNames, Dictionary lfLabels,
      List<FeatureVector<Double>> instances, eTieBreakPolicy tieBreakPolicy, double tolerance) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(lfLabels, "lfLabels should not be null");
    Preconditions.checkNotNull(instances, "instances should not be null");
    Preconditions.checkArgument(lfLabels.size() >= 2, "cardinality must be >= 2");
    Preconditions.checkArgument(tolerance >= 0, "tolerance must be >= 0");

    List<FeatureVector<Double>> yp = instances;

    // diffs[n][k] with n = dataset.size() and k = the number of distinct labels i.e. the
    // cardinality. diffs[n][k] is the label with the highest probability.
    List<FeatureVector<Double>> diffs = new ArrayList<>(yp.size());

    for (FeatureVector<Double> ypi : yp) {

      // Get the label with the highest probability
      @Var
      double max = 0;

      for (int k = 0; k < ypi.size(); k++) {
        if (ypi.get(k) > max) {
          max = ypi.get(k);
        }
      }

      // For each label, compute the distance between the current label probability and the highest
      // probability found
      for (int k = 0; k < ypi.size(); k++) {
        ypi.set(k, Math.abs(ypi.get(k) - max));
      }

      diffs.add(ypi);
    }

    // Try predicting the label being associated with each instance using labeling functions
    Random rand = new Random();
    List<Integer> predictions = new ArrayList<>(diffs.size());

    for (FeatureVector<Double> vector : diffs) {

      List<Integer> maxIndexes = new ArrayList<>();

      for (int k = 0; k < vector.size(); k++) {
        if (vector.get(k) < tolerance) {
          maxIndexes.add(k);
        }
      }

      if (maxIndexes.isEmpty()) {
        predictions.add(LabelingFunction.ABSTAIN); // TODO : not sure about this
      } else if (maxIndexes.size() == 1) {
        predictions.add(maxIndexes.get(0));
      } else if (tieBreakPolicy.equals(eTieBreakPolicy.RANDOM)) {
        predictions.add(maxIndexes.get(predictions.size() % maxIndexes.size()));
      } else if (tieBreakPolicy.equals(eTieBreakPolicy.TRUE_RANDOM)) {
        predictions.add(maxIndexes.get(rand.nextInt(maxIndexes.size())));
      } else if (tieBreakPolicy.equals(eTieBreakPolicy.ABSTAIN)) {
        predictions.add(ABSTAIN);
      } else {
        Preconditions.checkState(false, "Invalid tie-break policy : %s", tieBreakPolicy);
      }
    }
    return predictions;
  }

  public enum eTieBreakPolicy {
    RANDOM, /* randomly choose among tied option in a predictable way */
    TRUE_RANDOM, /* randomly choose among the tied options */
    ABSTAIN /* return an abstain vote */
  }
}
