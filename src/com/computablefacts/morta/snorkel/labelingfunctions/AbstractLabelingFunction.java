package com.computablefacts.morta.snorkel.labelingfunctions;

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
}
