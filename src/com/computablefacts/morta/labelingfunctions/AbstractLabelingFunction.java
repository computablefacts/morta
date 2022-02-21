package com.computablefacts.morta.labelingfunctions;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

/**
 * Project a data point to an integer.
 *
 * @param <T> input type.
 */
@CheckReturnValue
public abstract class AbstractLabelingFunction<T> implements Function<T, Integer> {

  public static final int ABSTAIN = -1;
  public static final int KO = 0;
  public static final int OK = 1;

  private final String name_;

  public AbstractLabelingFunction(String name) {
    name_ = Preconditions.checkNotNull(name, "name should not be null");
  }

  public String name() {
    return name_;
  }

  public Set<String> matches(String text) {
    return new HashSet<>();
  }

  public double weight() {
    return 1; // must be between 0 (worst) and 1 (best)
  }
}
