package com.computablefacts.morta;

import static com.computablefacts.morta.LabelingFunction.ABSTAIN;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Var;

final public class MajorityLabelModel {

  private MajorityLabelModel() {}

  public static List<FeatureVector<Double>> probabilities(Dictionary lfNames, Dictionary lfOutputs,
      List<FeatureVector<Integer>> instances) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(lfOutputs, "lfOutputs should not be null");
    Preconditions.checkNotNull(instances, "instances should not be null");

    int nbLabelingFunctions = lfNames.size();
    int cardinality = lfOutputs.size();

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
}
