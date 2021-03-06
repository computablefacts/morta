package com.computablefacts.morta.snorkel;

import java.util.function.Function;

/**
 * Transform any data type to another data type.
 *
 * @param <I> input type.
 * @param <O> output type.
 */
@FunctionalInterface
public interface ITransformationFunction<I, O> extends Function<I, O> {
}
