package com.computablefacts.morta;

import static com.computablefacts.morta.ILabelingFunction.ABSTAIN;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.computablefacts.nona.Generated;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

import smile.stat.hypothesis.CorTest;

@CheckReturnValue
final public class Summary {

  private final String label_;
  private final Set<String> polarity_;
  private final double coverage_;
  private final double overlaps_;
  private final double conflicts_;
  private final int correct_;
  private final int incorrect_;

  public Summary(String label, Set<String> polarity, double coverage, double overlaps,
      double conflicts, int correct, int incorrect) {

    label_ = Preconditions.checkNotNull(label, "label should not be null");
    polarity_ = Preconditions.checkNotNull(polarity, "polarity should not be null");
    coverage_ = coverage;
    overlaps_ = overlaps;
    conflicts_ = conflicts;
    correct_ = correct;
    incorrect_ = incorrect;
  }

  /**
   * Compute correlation between each pair of labeling functions.
   *
   * @param lfNames mapping of the labeling function names to integers. Each integer represents the
   *        position of the labeling function in the lfs list.
   * @param lfLabels mapping of the labeling function outputs, i.e. labels, to integers. Each
   *        integer represents a machine-friendly version of a human-readable label.
   * @param instances output of the labeling functions for each datapoint.
   * @param correlation correlation type.
   * @return a correlation matrix.
   */
  public static <T> Table<String, String, CorTest> labelingFunctionsCorrelations(Dictionary lfNames,
      Dictionary lfLabels, List<Map.Entry<T, FeatureVector<Integer>>> instances,
      eCorrelation correlation) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(lfLabels, "lfLabels should not be null");
    Preconditions.checkNotNull(instances, "instances should not be null");
    Preconditions.checkNotNull(correlation, "correlation should not be null");

    int nbLabelingFunctions = lfNames.size();
    List<double[]> matrix = new ArrayList<>(nbLabelingFunctions);

    // Transpose
    for (int i = 0; i < nbLabelingFunctions; i++) {

      double[] vector = new double[instances.size()];

      for (int j = 0; j < instances.size(); j++) {
        vector[j] = instances.get(j).getValue().get(i);
      }

      matrix.add(vector);
    }

    // Compute correlation coefficient between each LF
    Table<String, String, CorTest> correlations = HashBasedTable.create();

    for (int i = 0; i < matrix.size(); i++) {
      for (int j = 0; j < matrix.size(); j++) {

        double[] lf1 = matrix.get(i);
        double[] lf2 = matrix.get(j);

        if (eCorrelation.KENDALL.equals(correlation)) {
          correlations.put(lfNames.label(i), lfNames.label(j), CorTest.kendall(lf1, lf2));
        } else if (eCorrelation.SPEARMAN.equals(correlation)) {
          correlations.put(lfNames.label(i), lfNames.label(j), CorTest.spearman(lf1, lf2));
        } else { // PEARSON
          correlations.put(lfNames.label(i), lfNames.label(j), CorTest.pearson(lf1, lf2));
        }
      }
    }
    return correlations;
  }

  /**
   * Explore the labeling functions outputs.
   *
   * @param lfNames mapping of the labeling function names to integers. Each integer represents the
   *        position of the labeling function in the lfs list.
   * @param lfLabels mapping of the labeling function outputs, i.e. labels, to integers. Each
   *        integer represents a machine-friendly version of a human-readable label.
   * @param instances output of the labeling functions for each datapoint.
   * @param goldLabels gold labels.
   * @return a segmentation of the data according to the output produced by each labeling function.
   */
  public static <T> Table<String, eStatus, List<Map.Entry<T, FeatureVector<Integer>>>> explore(
      Dictionary lfNames, Dictionary lfLabels, List<Map.Entry<T, FeatureVector<Integer>>> instances,
      List<Integer> goldLabels) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(lfLabels, "lfNames should not be null");
    Preconditions.checkNotNull(instances, "instances should not be null");
    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    Preconditions.checkArgument(instances.size() == goldLabels.size(),
        "Mismatch between the number of instances and the number of gold labels : %s vs %s",
        instances.size(), goldLabels.size());

    int nbLabelingFunctions = lfNames.size();
    Table<String, eStatus, List<Map.Entry<T, FeatureVector<Integer>>>> table =
        HashBasedTable.create();

    for (int i = 0; i < nbLabelingFunctions; i++) {

      String lfName = lfNames.label(i);

      for (int j = 0; j < instances.size(); j++) {

        Map.Entry<T, FeatureVector<Integer>> instance = instances.get(j);
        int lfLabel = instance.getValue().get(i);

        Preconditions.checkState(nbLabelingFunctions == instance.getValue().size(),
            "Invalid feature vector length : %s found vs %s expected", instance.getValue().size(),
            nbLabelingFunctions);

        if (lfLabel == goldLabels.get(j)) {
          if (lfLabel == ABSTAIN) {
            if (!table.contains(lfName, eStatus.CORRECT_ABSTAIN)) {
              table.put(lfName, eStatus.CORRECT_ABSTAIN, new ArrayList<>());
            }
            table.get(lfName, eStatus.CORRECT_ABSTAIN).add(instance);
          } else {
            if (!table.contains(lfName, eStatus.CORRECT)) {
              table.put(lfName, eStatus.CORRECT, new ArrayList<>());
            }
            table.get(lfName, eStatus.CORRECT).add(instance);
          }
        } else {
          if (lfLabel == ABSTAIN) {
            if (!table.contains(lfName, eStatus.INCORRECT_ABSTAIN)) {
              table.put(lfName, eStatus.INCORRECT_ABSTAIN, new ArrayList<>());
            }
            table.get(lfName, eStatus.INCORRECT_ABSTAIN).add(instance);
          } else {
            if (!table.contains(lfName, eStatus.INCORRECT)) {
              table.put(lfName, eStatus.INCORRECT, new ArrayList<>());
            }
            table.get(lfName, eStatus.INCORRECT).add(instance);
          }
        }
      }
    }
    return table;
  }

  /**
   * Compute a {@link Summary} object with polarity, coverage, overlaps, etc. for each labeling
   * function. When gold labels are provided, this method will compute the number of correct and
   * incorrect labels output by each labeling function.
   *
   * @param lfNames lfNames mapping of the labeling function names to integers. Each integer
   *        represents the position of the labeling function in the lfs list.
   * @param lfLabels mapping of the labeling function outputs, i.e. labels, to integers. Each
   *        integer represents a machine-friendly version of a human-readable label.
   * @param instances output of the labeling functions for each datapoint.
   * @param goldLabels gold labels (optional).
   * @return a {@link Summary} object for each labeling function.
   */
  public static <T> List<Summary> summarize(Dictionary lfNames, Dictionary lfLabels,
      List<Map.Entry<T, FeatureVector<Integer>>> instances, List<Integer> goldLabels) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(lfLabels, "lfLabels should not be null");
    Preconditions.checkNotNull(instances, "instances should not be null");

    int nbLabelingFunctions = lfNames.size();
    List<Summary> summaries = new ArrayList<>(nbLabelingFunctions);

    Preconditions.checkState(goldLabels == null || instances.size() == goldLabels.size(),
        "Mismatch between the number of instances and the number of gold labels : %s vs %s",
        instances.size(), goldLabels == null ? 0 : goldLabels.size());

    for (int i = 0; i < nbLabelingFunctions; i++) {

      String labelingFunctionName = lfNames.label(i);
      Set<String> labels = new HashSet<>();
      @Var
      double nbLabelled = 0;
      @Var
      double nbOverlaps = 0;
      @Var
      double nbConflicts = 0;
      @Var
      double nbDataPoints = 0;
      @Var
      int nbCorrect = goldLabels == null ? -1 : 0;
      @Var
      int nbIncorrect = goldLabels == null ? -1 : 0;

      for (int j = 0; j < instances.size(); j++) {

        nbDataPoints += 1.0;
        Map.Entry<T, FeatureVector<Integer>> featureVector = instances.get(j);
        String lfName = lfNames.label(i);
        int lfValue = featureVector.getValue().get(i);

        Preconditions.checkState(nbLabelingFunctions == featureVector.getValue().size(),
            "Invalid feature vector length : %s found vs %s expected",
            featureVector.getValue().size(), nbLabelingFunctions);
        Preconditions.checkState(labelingFunctionName.equals(lfName),
            "Invalid labeling function name : %s found vs %s expected", lfName,
            labelingFunctionName);

        if (lfValue > ABSTAIN) {
          if (goldLabels != null) {
            if (lfValue == goldLabels.get(j)) {
              nbCorrect++;
            } else {
              nbIncorrect++;
            }
          }

          nbLabelled += 1.0;
          labels.add(lfLabels.label(lfValue));

          @Var
          boolean hasOverlap = false;
          @Var
          boolean hasConflict = false;

          for (int k = 0; (!hasOverlap || !hasConflict) && k < nbLabelingFunctions; k++) {
            if (k != i) {

              int lfv = featureVector.getValue().get(k);

              if (!hasOverlap && lfv > ABSTAIN && lfv == lfValue) {
                nbOverlaps += 1.0;
                hasOverlap = true;
              }
              if (!hasConflict && lfv > ABSTAIN && lfv != lfValue) {
                nbConflicts += 1.0;
                hasConflict = true;
              }
            }
          }
        }
      }

      Preconditions.checkState(goldLabels == null || nbCorrect + nbIncorrect == instances.size(),
          "Mismatch between the number of correct/incorrect labels and the number of instances : %s found vs %s expected",
          nbCorrect + nbIncorrect, instances.size());

      summaries.add(new Summary(labelingFunctionName, labels, nbLabelled / nbDataPoints,
          nbOverlaps / nbLabelled, nbConflicts / nbLabelled, nbCorrect, nbIncorrect));
    }
    return summaries;
  }

  @Generated
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("label", label_).add("polarity", polarity_)
        .add("coverage", coverage_).add("overlaps", overlaps_).add("conflicts", conflicts_)
        .add("correct", correct_).add("incorrect", incorrect_).toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof Summary)) {
      return false;
    }
    Summary summary = (Summary) obj;
    return Objects.equal(label_, summary.label_) && Objects.equal(polarity_, summary.polarity_)
        && Objects.equal(coverage_, summary.coverage_)
        && Objects.equal(overlaps_, summary.overlaps_)
        && Objects.equal(conflicts_, summary.conflicts_)
        && Objects.equal(correct_, summary.correct_)
        && Objects.equal(incorrect_, summary.incorrect_);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(label_, polarity_, coverage_, overlaps_, conflicts_, correct_,
        incorrect_);
  }

  /**
   * The name of the considered LF.
   *
   * @return the LF's name
   */
  @Generated
  public String label() {
    return label_;
  }

  /**
   * Polarity: The set of unique labels this LF outputs (excluding abstains)
   *
   * @return polarity
   */
  @Generated
  public Set<String> polarity() {
    return polarity_;
  }

  /**
   * Coverage: The fraction of the dataset this LF labels
   *
   * @return coverage
   */
  @Generated
  public double coverage() {
    return coverage_;
  }

  /**
   * Overlaps: The fraction of the dataset where this LF and at least one other LF label
   *
   * @return overlaps
   */
  @Generated
  public double overlaps() {
    return overlaps_;
  }

  /**
   * Conflicts: The fraction of the dataset where this LF and at least one other LF label and
   * disagree
   *
   * @return conflicts
   */
  @Generated
  public double conflicts() {
    return conflicts_;
  }

  /**
   * Correct: The number of data points this LF labels correctly (if gold labels are provided)
   *
   * @return correct
   */
  @Generated
  public int correct() {
    return correct_;
  }

  /**
   * Incorrect: The number of data points this LF labels incorrectly (if gold labels are provided)
   *
   * @return incorrect
   */
  @Generated
  public int incorrect() {
    return incorrect_;
  }

  // TODO : compute empirical accuracy

  /**
   * Note that Spearman is computed on ranks and so depicts monotonic relationships while Pearson is
   * on true values and depicts linear relationships. If Spearman > Pearson the correlation is
   * monotonic but not linear.
   */
  public enum eCorrelation {
    PEARSON, KENDALL, SPEARMAN
  }

  public enum eStatus {
    ALL, CORRECT, /* the LF output the same label as the gold one */
    INCORRECT, /* the LF output a label different from the gold one */
    CORRECT_ABSTAIN, /* both the LF and the gold label are ABSTAIN */
    INCORRECT_ABSTAIN /* the LF output is ABSTAIN but the gold one is not */
  }
}
