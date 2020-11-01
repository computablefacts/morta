package com.computablefacts.morta;

import static com.computablefacts.morta.ILabelingFunction.ABSTAIN;

import java.util.AbstractMap;
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
  }
}
