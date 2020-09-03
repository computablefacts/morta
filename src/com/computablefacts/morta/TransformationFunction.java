package com.computablefacts.morta;

import java.util.function.Function;

/**
 * Transform any data type to another data type.
 *
 * @param <I> input type.
 * @param <O> output type.
 */
@FunctionalInterface
public interface TransformationFunction<I, O> extends Function<I, O> {
}
