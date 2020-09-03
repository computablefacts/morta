package com.computablefacts.morta;

import static com.computablefacts.morta.LabelingFunction.ABSTAIN;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

/**
 * Order and execute one or more of the following functions :
 *
 * <ul>
 * <li>{@link TransformationFunction}</li>
 * <li>{@link SlicingFunction}</li>
 * <li>{@link LabelingFunction}</li>
 * </ul>
 */
@CheckReturnValue
final public class Pipeline {

  private Pipeline() {}

  public static <D> Builder<D> on(Collection<D> dataset) {
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

    public Builder<D> slice(SlicingFunction<D> slice) {

      Preconditions.checkNotNull(slice, "slice should not be null");

      return new Builder<>(stream_.filter(slice));
    }

    public <O> Builder<O> transform(TransformationFunction<D, O> transform) {

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
    public Builder<Map.Entry<D, FeatureVector<Integer>>> label(List<LabelingFunction<D>> lfs) {

      Preconditions.checkNotNull(lfs, "lfs should not be null");

      return new Builder<>(stream_.map(d -> {

        FeatureVector<Integer> vector = new FeatureVector<>(lfs.size(), ABSTAIN);

        for (int i = 0; i < lfs.size(); i++) {
          LabelingFunction<D> lf = lfs.get(i);
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
    public Builder<FeatureVector<Integer>> labels(List<LabelingFunction<D>> lfs) {
      return label(lfs).transform(Map.Entry::getValue);
    }

    /**
     * Compute the probability of each label using a majority vote.
     *
     * @param lfNames mapping of the labeling function names to integers. Each integer represents
     *        the position of the labeling function in the lfs list.
     * @param lfOutputs lfOutputs mapping of the labeling function outputs, i.e. labels, to
     *        integers. Each integer represents a machine-friendly version of a human-readable
     *        label.
     * @param lfs labeling functions.
     * @return a {@link FeatureVector} for each data point. Each column of the {@link FeatureVector}
     *         represents a distinct label. Thus, the {@link FeatureVector} length is equal to the
     *         number of labels.
     */
    public List<FeatureVector<Double>> probabilities(Dictionary lfNames, Dictionary lfOutputs,
        List<LabelingFunction<D>> lfs) {
      return MajorityLabelModel.probabilities(lfNames, lfOutputs, labels(lfs).collect());
    }

    /**
     * Try to predict the label associated with each data point using a majority vote.
     *
     * @param lfNames mapping of the labeling function names to integers. Each integer represents
     *        the position of the labeling function in the lfs list.
     * @param lfOutputs lfOutputs mapping of the labeling function outputs, i.e. labels, to
     *        integers. Each integer represents a machine-friendly version of a human-readable
     *        label.
     * @param lfs labeling functions.
     * @param tieBreakPolicy tie-break policy.
     * @return a single label for each data point.
     */
    public List<Integer> predictions(Dictionary lfNames, Dictionary lfOutputs,
        List<LabelingFunction<D>> lfs, MajorityLabelModel.eTieBreakPolicy tieBreakPolicy) {
      return MajorityLabelModel.predictions(lfNames, lfOutputs,
          probabilities(lfNames, lfOutputs, lfs), tieBreakPolicy, 0.00001);
    }

    /**
     * Compute the accuracy of the predictions made using a majority vote.
     * 
     * @param lfNames mapping of the labeling function names to integers. Each integer represents
     *        the position of the labeling function in the lfs list.
     * @param lfOutputs lfOutputs mapping of the labeling function outputs, i.e. labels, to
     *        integers. Each integer represents a machine-friendly version of a human-readable
     *        label.
     * @param lfs labeling functions.
     * @param tieBreakPolicy tie-break policy.
     * @param goldLabels gold labels.
     * @return a list with two elements. The first element is the number of accurately labeled data
     *         points. The second element is the number of inexactly labeled data points.
     */
    public List<Integer> accuracy(Dictionary lfNames, Dictionary lfOutputs,
        List<LabelingFunction<D>> lfs, MajorityLabelModel.eTieBreakPolicy tieBreakPolicy,
        List<Integer> goldLabels) {
      return ModelChecker.accuracy(predictions(lfNames, lfOutputs, lfs, tieBreakPolicy),
          goldLabels);
    }

    /**
     * Compute a {@link Summary} object with polarity, coverage, overlaps, etc. for each labeling
     * function. When gold labels are provided, this method will compute the number of correct and
     * incorrect labels output by each labeling function.
     *
     * @param lfNames mapping of the labeling function names to integers. Each integer represents
     *        the name of a labeling function (and its position in the lfs list).
     * @param lfOutputs mapping of the labeling function outputs, i.e. labels, to integers. Each
     *        integer represents a machine-friendly version of a human-readable label.
     * @param lfs labeling functions.
     * @param goldLabels gold labels (optional).
     * @return a {@link Summary} object for each labeling function.
     */
    public List<Summary> summaries(Dictionary lfNames, Dictionary lfOutputs,
        List<LabelingFunction<D>> lfs, List<Integer> goldLabels) {
      return Summarizer.summaries(lfNames, lfOutputs, labels(lfs).collect(), goldLabels);
    }
  }
}
