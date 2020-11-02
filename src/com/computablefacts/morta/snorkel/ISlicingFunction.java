package com.computablefacts.morta.snorkel;

import java.util.function.Predicate;

/**
 * Test a data point. The {@link ISlicingFunction#test} method returns true if the data point should
 * be kept, false otherwise.
 * 
 * @param <I> input type.
 */
@FunctionalInterface
public interface ISlicingFunction<I> extends Predicate<I> {
}
