package com.computablefacts.morta;

import java.util.Map;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class GoldLabel implements IGoldLabel<String> {

  private final Map<String, Object> json_;

  public GoldLabel(Map<String, Object> json) {
    json_ = Preconditions.checkNotNull(json, "json should not be null");
  }

  @Override
  public boolean equals(Object obj) {
    return json_.equals(obj);
  }

  @Override
  public int hashCode() {
    return json_.hashCode();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("id", id()).add("label", label())
        .add("data", data()).add("is_true_positive", isTruePositive())
        .add("is_false_positive", isFalsePositive()).add("is_true_negative", isTrueNegative())
        .add("is_false_negative", isFalseNegative()).omitNullValues().toString();
  }

  @Override
  public String id() {
    return (String) json_.get("id");
  }

  @Override
  public String label() {
    return (String) json_.get("label");
  }

  @Override
  public String data() {
    return (String) json_.get("data");
  }

  @Override
  public boolean isTruePositive() {
    return (Boolean) json_.get("is_true_positive");
  }

  @Override
  public boolean isFalsePositive() {
    return (Boolean) json_.get("is_false_positive");
  }

  @Override
  public boolean isTrueNegative() {
    return (Boolean) json_.get("is_true_negative");
  }

  @Override
  public boolean isFalseNegative() {
    return (Boolean) json_.get("is_false_negative");
  }
}
