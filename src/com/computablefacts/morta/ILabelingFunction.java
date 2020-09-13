package com.computablefacts.morta;

/**
 * Project a data point to an integer.
 *
 * @param <I> input type.
 */
@FunctionalInterface
public interface ILabelingFunction<I> extends ITransformationFunction<I, Integer> {

  int ABSTAIN = -1;
}
