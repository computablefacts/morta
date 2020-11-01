package com.computablefacts.morta;

import static com.computablefacts.morta.ILabelingFunction.ABSTAIN;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

import smile.stat.hypothesis.CorTest;

/**
 * Order and execute one or more of the following functions :
 *
 * <ul>
 * <li>{@link ITransformationFunction}</li>
 * <li>{@link ISlicingFunction}</li>
 * <li>{@link ILabelingFunction}</li>
 * </ul>
 */
@CheckReturnValue
final public class Pipeline {

  private Pipeline() {}

  public static <D> Builder<D> on(List<D> dataset) {
    return on(dataset.stream());
  }

  public static <D> Builder<D> on(Stream<D> dataset) {
    return new Builder<>(dataset);
  }

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

  public static class Builder<D> {

    private final Stream<D> stream_;

    private Builder(Stream<D> stream) {
      stream_ = Preconditions.checkNotNull(stream, "stream should not be null");
    }

    public List<D> collect() {
      return stream_.collect(Collectors.toList());
    }

    public Builder<D> slice(ISlicingFunction<D> slice) {

      Preconditions.checkNotNull(slice, "slice should not be null");

      return new Builder<>(stream_.filter(slice));
    }

    public <O> Builder<O> transform(ITransformationFunction<D, O> transform) {

      Preconditions.checkNotNull(transform, "transform should not be null");

      return new Builder<>(stream_.map(transform));
    }

    /**
     * For each data point, get the label output by each labeling functions.
     *
     * @param lfs labeling functions.
     * @return pairs of (data point, {@link FeatureVector}). Each column of the
     *         {@link FeatureVector} represents a distinct labeling function output. The first
     *         feature is the output of the first labeling function, the second feature is the
     *         output of the second labeling function, etc. Thus, the {@link FeatureVector} length
     *         is equal to the number of labeling functions.
     */
    public Builder<Map.Entry<D, FeatureVector<Integer>>> label(List<ILabelingFunction<D>> lfs) {

      Preconditions.checkNotNull(lfs, "lfs should not be null");

      return new Builder<>(stream_.map(d -> {

        FeatureVector<Integer> vector = new FeatureVector<>(lfs.size(), ABSTAIN);

        for (int i = 0; i < lfs.size(); i++) {
          ILabelingFunction<D> lf = lfs.get(i);
          int label = lf.apply(d);
          vector.set(i, label);
        }
        return new AbstractMap.SimpleEntry<>(d, vector);
      }));
    }

    /**
     * For each data point, get the label output by each labeling functions.
     *
     * @param lfs labeling functions.
     * @return a {@link FeatureVector} for each data point. Each column of the {@link FeatureVector}
     *         represents a distinct labeling function output. The first feature is the output of
     *         the first labeling function, the second feature is the output of the second labeling
     *         function, etc. Thus, the {@link FeatureVector} length is equal to the number of
     *         labeling functions.
     */
    public Builder<FeatureVector<Integer>> labels(List<ILabelingFunction<D>> lfs) {
      return label(lfs).transform(Map.Entry::getValue);
    }

    /**
     * Compute the probability of each label using a majority vote.
     *
     * @param lfNames mapping of the labeling function names to integers. Each integer represents
     *        the position of the labeling function in the lfs list.
     * @param lfLabels mapping of the labeling function outputs, i.e. labels, to integers. Each
     *        integer represents a machine-friendly version of a human-readable label.
     * @param lfs labeling functions.
     * @return a {@link FeatureVector} for each data point. Each column of the {@link FeatureVector}
     *         represents a distinct label. Thus, the {@link FeatureVector} length is equal to the
     *         number of labels.
     */
    public List<FeatureVector<Double>> probabilities(Dictionary lfNames, Dictionary lfLabels,
        List<ILabelingFunction<D>> lfs) {
      return MajorityLabelModel.probabilities(lfNames, lfLabels, labels(lfs).collect());
    }

    /**
     * Try to predict the label associated with each data point using a majority vote.
     *
     * @param lfNames mapping of the labeling function names to integers. Each integer represents
     *        the position of the labeling function in the lfs list.
     * @param lfLabels mapping of the labeling function outputs, i.e. labels, to integers. Each
     *        integer represents a machine-friendly version of a human-readable label.
     * @param lfs labeling functions.
     * @param tieBreakPolicy tie-break policy.
     * @return a single label for each data point.
     */
    public List<Integer> predictions(Dictionary lfNames, Dictionary lfLabels,
        List<ILabelingFunction<D>> lfs, MajorityLabelModel.eTieBreakPolicy tieBreakPolicy) {
      return MajorityLabelModel.predictions(lfNames, lfLabels,
          probabilities(lfNames, lfLabels, lfs), tieBreakPolicy, 0.00001);
    }

    /**
     * Compute correlation between each pair of labeling functions.
     *
     * @param lfNames mapping of the labeling function names to integers. Each integer represents
     *        the position of the labeling function in the lfs list.
     * @param lfs labeling functions.
     * @param correlation correlation type.
     * @return a correlation matrix.
     */
    public Table<String, String, CorTest> labelingFunctionsCorrelations(Dictionary lfNames,
        List<ILabelingFunction<D>> lfs, eCorrelation correlation) {

      Preconditions.checkNotNull(lfNames, "lfNames should not be null");
      Preconditions.checkNotNull(lfs, "lfs should not be null");
      Preconditions.checkNotNull(correlation, "correlation should not be null");

      List<FeatureVector<Integer>> instances = labels(lfs).collect();
      int nbLabelingFunctions = lfNames.size();
      List<double[]> matrix = new ArrayList<>(nbLabelingFunctions);

      // Transpose
      for (int i = 0; i < nbLabelingFunctions; i++) {

        double[] vector = new double[instances.size()];

        for (int j = 0; j < instances.size(); j++) {
          vector[j] = instances.get(j).get(i);
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
     * @param lfNames mapping of the labeling function names to integers. Each integer represents
     *        the position of the labeling function in the lfs list.
     * @param lfs labeling functions.
     * @param goldLabels gold labels.
     * @return a segmentation of the data according to the output produced by each labeling
     *         function.
     */
    public Table<String, eStatus, List<Map.Entry<D, FeatureVector<Integer>>>> explore(
        Dictionary lfNames, List<ILabelingFunction<D>> lfs, List<Integer> goldLabels) {

      Preconditions.checkNotNull(lfNames, "lfNames should not be null");
      Preconditions.checkNotNull(lfs, "lfs should not be null");
      Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

      List<Map.Entry<D, FeatureVector<Integer>>> instances = label(lfs).collect();

      Preconditions.checkArgument(instances.size() == goldLabels.size(),
          "Mismatch between the number of instances and the number of gold labels : %s vs %s",
          instances.size(), goldLabels.size());

      int nbLabelingFunctions = lfNames.size();
      Table<String, eStatus, List<Map.Entry<D, FeatureVector<Integer>>>> table =
          HashBasedTable.create();

      for (int i = 0; i < nbLabelingFunctions; i++) {

        String lfName = lfNames.label(i);

        for (int j = 0; j < instances.size(); j++) {

          Map.Entry<D, FeatureVector<Integer>> instance = instances.get(j);
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
     * @param lfNames mapping of the labeling function names to integers. Each integer represents
     *        the name of a labeling function (and its position in the lfs list).
     * @param lfLabels mapping of the labeling function outputs, i.e. labels, to integers. Each
     *        integer represents a machine-friendly version of a human-readable label.
     * @param lfs labeling functions.
     * @param goldLabels gold labels (optional).
     * @return a {@link Summary} object for each labeling function.
     */
    public List<Summary> summaries(Dictionary lfNames, Dictionary lfLabels,
        List<ILabelingFunction<D>> lfs, List<Integer> goldLabels) {

      Preconditions.checkNotNull(lfNames, "lfNames should not be null");
      Preconditions.checkNotNull(lfLabels, "lfLabels should not be null");
      Preconditions.checkNotNull(lfs, "lfs should not be null");

      List<FeatureVector<Integer>> instances = labels(lfs).collect();
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
          FeatureVector<Integer> featureVector = instances.get(j);
          String lfName = lfNames.label(i);
          int lfValue = featureVector.get(i);

          Preconditions.checkState(nbLabelingFunctions == featureVector.size(),
              "Invalid feature vector length : %s found vs %s expected", featureVector.size(),
              nbLabelingFunctions);
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

                int lfv = featureVector.get(k);

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
  }
}
