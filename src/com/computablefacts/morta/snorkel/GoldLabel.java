package com.computablefacts.morta.snorkel;

import java.util.Map;
import java.util.Objects;

import com.computablefacts.asterix.Generated;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class GoldLabel implements IGoldLabel<String> {

  private final String id_;
  private final String label_;
  private final String data_;
  private final String snippet_;
  private final boolean isTruePositive_;
  private final boolean isFalsePositive_;
  private final boolean isTrueNegative_;
  private final boolean isFalseNegative_;

  public GoldLabel(Map<String, Object> json) {

    Preconditions.checkNotNull(json, "json should not be null");

    id_ = (String) Preconditions.checkNotNull(json.get("id"), "id should not be null");
    label_ = (String) Preconditions.checkNotNull(json.get("label"), "label should not be null");
    data_ = (String) Preconditions.checkNotNull(json.get("data"), "data should not be null");
    snippet_ = (String) json.get("snippet"); // optional
    isTruePositive_ = (Boolean) Preconditions.checkNotNull(json.get("is_true_positive"),
        "is_true_positive should not be null");
    isFalsePositive_ = (Boolean) Preconditions.checkNotNull(json.get("is_false_positive"),
        "is_false_positive should not be null");
    isTrueNegative_ = (Boolean) Preconditions.checkNotNull(json.get("is_true_negative"),
        "is_true_negative should not be null");
    isFalseNegative_ = (Boolean) Preconditions.checkNotNull(json.get("is_false_negative"),
        "is_false_negative should not be null");

    Preconditions.checkState(
        (isTruePositive_ ? 1 : 0) + (isFalsePositive_ ? 1 : 0) + (isTrueNegative_ ? 1 : 0)
            + (isFalseNegative_ ? 1 : 0) == 1,
        "Exactly one is_xxx flag must be set to true : (TP, FP, TN, FN) = (%s, %s, %s, %s)",
        isTruePositive_, isFalsePositive_, isTrueNegative_, isFalseNegative_);
  }

  public GoldLabel(String id, String label, String data, boolean isTruePositive,
      boolean isFalsePositive, boolean isTrueNegative, boolean isFalseNegative) {
    this(id, label, data, null, isTruePositive, isFalsePositive, isTrueNegative, isFalseNegative);
  }

  public GoldLabel(String id, String label, String data, String snippet, boolean isTruePositive,
      boolean isFalsePositive, boolean isTrueNegative, boolean isFalseNegative) {

    id_ = Preconditions.checkNotNull(id, "id should not be null");
    label_ = Preconditions.checkNotNull(label, "label should not be null");
    data_ = Preconditions.checkNotNull(data, "data should not be null");
    snippet_ = snippet; // optional
    isTruePositive_ = isTruePositive;
    isFalsePositive_ = isFalsePositive;
    isTrueNegative_ = isTrueNegative;
    isFalseNegative_ = isFalseNegative;

    Preconditions.checkState(
        (isTruePositive_ ? 1 : 0) + (isFalsePositive_ ? 1 : 0) + (isTrueNegative_ ? 1 : 0)
            + (isFalseNegative_ ? 1 : 0) == 1,
        "Exactly one is_xxx flag must be set to true : (TP, FP, TN, FN) = (%s, %s, %s, %s)",
        isTruePositive_, isFalsePositive_, isTrueNegative_, isFalseNegative_);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof GoldLabel)) {
      return false;
    }
    GoldLabel gl = (GoldLabel) obj;
    return com.google.common.base.Objects.equal(id(), gl.id())
        && com.google.common.base.Objects.equal(label(), gl.label())
        && com.google.common.base.Objects.equal(data(), gl.data())
        && com.google.common.base.Objects.equal(snippet(), gl.snippet())
        && com.google.common.base.Objects.equal(isTruePositive(), gl.isTruePositive())
        && com.google.common.base.Objects.equal(isFalsePositive(), gl.isFalsePositive())
        && com.google.common.base.Objects.equal(isTrueNegative(), gl.isTrueNegative())
        && com.google.common.base.Objects.equal(isFalseNegative(), gl.isFalseNegative());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id(), label(), data(), snippet(), isTruePositive(), isFalsePositive(),
        isTrueNegative(), isFalseNegative());
  }

  @Generated
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("id", id()).add("label", label())
        .add("data", data()).add("snippet", snippet()).add("is_true_positive", isTruePositive())
        .add("is_false_positive", isFalsePositive()).add("is_true_negative", isTrueNegative())
        .add("is_false_negative", isFalseNegative()).omitNullValues().toString();
  }

  @Override
  public String id() {
    return id_;
  }

  @Override
  public String label() {
    return label_;
  }

  @Override
  public String data() {
    return data_;
  }

  @Override
  public String snippet() {
    return snippet_;
  }

  @Override
  public boolean isTruePositive() {
    return isTruePositive_;
  }

  @Override
  public boolean isFalsePositive() {
    return isFalsePositive_;
  }

  @Override
  public boolean isTrueNegative() {
    return isTrueNegative_;
  }

  @Override
  public boolean isFalseNegative() {
    return isFalseNegative_;
  }
}
