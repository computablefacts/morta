package com.computablefacts.morta.labelingfunctions;

import java.util.HashSet;
import java.util.Set;

import com.computablefacts.morta.snorkel.ILabelingFunction;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
public abstract class AbstractLabelingFunction<T> implements ILabelingFunction<T> {

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
