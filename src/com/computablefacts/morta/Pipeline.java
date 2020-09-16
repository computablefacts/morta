package com.computablefacts.morta;

import static com.computablefacts.morta.ILabelingFunction.ABSTAIN;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;

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
        List<ILabelingFunction<D>> lfs, Explorer.eCorrelation correlation) {
      return Explorer.labelingFunctionsCorrelations(lfNames, labels(lfs).collect(), correlation);
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
    public Table<String, Explorer.eStatus, List<Map.Entry<D, FeatureVector<Integer>>>> explore(
        Dictionary lfNames, List<ILabelingFunction<D>> lfs, List<Integer> goldLabels) {
      return Explorer.explore(lfNames, label(lfs).collect(), goldLabels);
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
      return Summarizer.summaries(lfNames, lfLabels, labels(lfs).collect(), goldLabels);
    }
  }
}
