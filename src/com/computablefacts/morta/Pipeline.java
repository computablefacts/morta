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
     * Apply all labeling functions on each data point and return the label output by each of them
     * in a {@link FeatureVector}. The first feature is the output of the first labeling function,
     * the second feature is the output of the second labeling function, etc. Thus, the
     * {@link FeatureVector} length is equal to the number of labeling functions.
     * 
     * @param lfs labeling functions.
     * @return pairs of (data point, feature vector).
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
     * Apply all labeling functions on each data point and return the label output by each of them
     * in a {@link FeatureVector}. The first feature is the output of the first labeling function,
     * the second feature is the output of the second labeling function, etc. Thus, the
     * {@link FeatureVector} length is equal to the number of labeling functions.
     *
     * @param lfs labeling functions.
     * @return a single feature vector for each data point.
     */
    public Builder<FeatureVector<Integer>> labels(List<LabelingFunction<D>> lfs) {
      return label(lfs).transform(Map.Entry::getValue);
    }

    /**
     * Apply labeling functions on each data point. Return a {@link Summary} object with polarity,
     * coverage, overlaps, ... for each data point.
     *
     * @param lfNames mapping of the labeling function names to integers. Each integer represents
     *        the position of the labeling function in the lfs list.
     * @param lfOutputs mapping of the labeling function outputs to integers. Each integer
     *        represents a machine-friendly version of a human-readable label.
     * @param lfs labeling functions.
     * @return a {@link Summary} object for each labeling function.
     */
    public List<Summary> summaries(Dictionary lfNames, Dictionary lfOutputs,
        List<LabelingFunction<D>> lfs) {
      return summaries(lfNames, lfOutputs, lfs, null);
    }

    /**
     * Apply labeling functions on each data point. Return a {@link Summary} object with polarity,
     * coverage, overlaps, ... for each data point. Furthermore, because gold labels are provided,
     * this method will compute the number of correct and incorrect labels output by each labeling
     * function.
     *
     * @param lfNames mapping of the labeling function names to integers. Each integer represents
     *        the name of a labeling function (and its position in the lfs list).
     * @param lfOutputs mapping of the labeling function outputs to integers. Each integer
     *        represents a machine-friendly version of a human-readable label.
     * @param lfs labeling functions.
     * @param goldLabels gold labels.
     * @return a {@link Summary} object for each labeling function.
     */
    public List<Summary> summaries(Dictionary lfNames, Dictionary lfOutputs,
        List<LabelingFunction<D>> lfs, List<Integer> goldLabels) {
      return Summarizer.summaries(lfNames, lfOutputs, labels(lfs).collect(), goldLabels);
    }
  }
}
