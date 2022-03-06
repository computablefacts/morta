package com.computablefacts.morta.labelmodels;

import static com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.computablefacts.asterix.View;
import com.computablefacts.morta.Dictionary;
import com.computablefacts.morta.FeatureVector;
import com.computablefacts.morta.Helpers;
import com.computablefacts.morta.IGoldLabel;
import com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

@CheckReturnValue
final public class MajorityLabelModel<T> extends AbstractLabelModel<T> {

  private final eTieBreakPolicy tieBreakPolicy_;
  private final double tolerance_;

  public MajorityLabelModel(MajorityLabelModel<T> labelModel) {
    this(labelModel.lfs(), labelModel.tieBreakPolicy(), labelModel.tolerance());
  }

  public MajorityLabelModel(List<? extends AbstractLabelingFunction<T>> lfs) {
    this(lfs, eTieBreakPolicy.RANDOM, 0.00001);
  }

  private MajorityLabelModel(List<? extends AbstractLabelingFunction<T>> lfs,
      eTieBreakPolicy tieBreakPolicy, double tolerance) {

    super(lfsNames(lfs), lfsLabels(), lfs);

    tieBreakPolicy_ = tieBreakPolicy == null ? eTieBreakPolicy.RANDOM : tieBreakPolicy;
    tolerance_ = tolerance < 0 ? 0.00001 : tolerance;
  }

  private static <T> Dictionary lfsNames(List<? extends AbstractLabelingFunction<T>> lfs) {

    Preconditions.checkNotNull(lfs, "lfs should not be null");

    Dictionary lfNames = new Dictionary();

    for (int i = 0; i < lfs.size(); i++) {
      lfNames.put(lfs.get(i).name(), i);
    }
    return lfNames;
  }

  private static Dictionary lfsLabels() {

    Dictionary lfOutputs = new Dictionary();
    lfOutputs.put("KO", KO);
    lfOutputs.put("OK", OK);

    return lfOutputs;
  }

  /**
   * Compute the probability of each label using a majority vote.
   *
   * @param lfNames mapping of the labeling function names to integers. Each integer represents the
   *        position of the labeling function in the lfs list.
   * @param lfLabels mapping of the labeling function outputs, i.e. labels, to integers. Each
   *        integer represents a machine-friendly version of a human-readable label.
   * @param instances output of the labeling functions for each datapoint.
   * @return a {@link FeatureVector} for each data point. Each column of the {@link FeatureVector}
   *         represents a distinct label. Thus, the {@link FeatureVector} length is equal to the
   *         number of labels.
   */
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

  /**
   * Try to predict the label associated with each data point using a majority vote.
   *
   * @param lfNames mapping of the labeling function names to integers. Each integer represents the
   *        position of the labeling function in the lfs list.
   * @param lfLabels mapping of the labeling function outputs, i.e. labels, to integers. Each
   *        integer represents a machine-friendly version of a human-readable label.
   * @param probabilities probabilities.
   * @param tieBreakPolicy tie-break policy.
   * @return a single label for each data point.
   */
  public static List<Integer> predictions(Dictionary lfNames, Dictionary lfLabels,
      List<FeatureVector<Double>> probabilities, eTieBreakPolicy tieBreakPolicy, double tolerance) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(lfLabels, "lfLabels should not be null");
    Preconditions.checkNotNull(probabilities, "probabilities should not be null");
    Preconditions.checkArgument(lfLabels.size() >= 2, "cardinality must be >= 2");
    Preconditions.checkArgument(tolerance >= 0, "tolerance must be >= 0");

    List<FeatureVector<Double>> yp = probabilities;

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
        predictions.add(ABSTAIN); // TODO : not sure about this
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

  @Override
  public void fit(List<? extends IGoldLabel<T>> goldLabels) {}

  @Override
  public List<Integer> predict(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return predictions(lfNames(), lfLabels(),
        probabilities(lfNames(), lfLabels(), View.of(goldLabels).map(IGoldLabel::data)
            .map(Helpers.label(lfs())).map(Map.Entry::getValue).toList()),
        tieBreakPolicy_, tolerance_);
  }

  public eTieBreakPolicy tieBreakPolicy() {
    return tieBreakPolicy_;
  }

  public double tolerance() {
    return tolerance_;
  }

  public enum eTieBreakPolicy {
    RANDOM, /* randomly choose among tied option in a predictable way */
    TRUE_RANDOM, /* randomly choose among the tied options */
    ABSTAIN /* return an abstain vote */
  }
}
