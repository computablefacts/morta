package com.computablefacts.morta.snorkel;

/**
 * Project a data point to an integer.
 *
 * @param <I> input type.
 */
@FunctionalInterface
public interface ILabelingFunction<I> extends ITransformationFunction<I, Integer> {

  int ABSTAIN = -1;
  int KO = 0;
  int OK = 1;
}
