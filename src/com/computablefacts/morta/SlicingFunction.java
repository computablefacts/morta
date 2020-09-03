package com.computablefacts.morta;

import java.util.function.Predicate;

/**
 * Test a data point. The {@link SlicingFunction#test} method returns true if the data point should
 * be kept, false otherwise.
 * 
 * @param <I> input type.
 */
@FunctionalInterface
public interface SlicingFunction<I> extends Predicate<I> {
}
