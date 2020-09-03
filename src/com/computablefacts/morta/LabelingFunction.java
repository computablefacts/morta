package com.computablefacts.morta;

/**
 * Project a data point to an integer.
 *
 * @param <I> input type.
 */
@FunctionalInterface
public interface LabelingFunction<I> extends TransformationFunction<I, Integer> {

  int ABSTAIN = -1;
}
