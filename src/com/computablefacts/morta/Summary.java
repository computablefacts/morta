package com.computablefacts.morta;

import java.util.Set;

import com.computablefacts.nona.Generated;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class Summary {

  private final String label_;
  private final Set<String> polarity_;
  private final double coverage_;
  private final double overlaps_;
  private final double conflicts_;
  private final int correct_;
  private final int incorrect_;

  public Summary(String label, Set<String> polarity, double coverage, double overlaps,
      double conflicts, int correct, int incorrect) {

    label_ = Preconditions.checkNotNull(label, "label should not be null");
    polarity_ = Preconditions.checkNotNull(polarity, "polarity should not be null");
    coverage_ = coverage;
    overlaps_ = overlaps;
    conflicts_ = conflicts;
    correct_ = correct;
    incorrect_ = incorrect;
  }

  @Generated
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("label", label_).add("polarity", polarity_)
        .add("coverage", coverage_).add("overlaps", overlaps_).add("conflicts", conflicts_)
        .add("correct", correct_).add("incorrect", incorrect_).toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof Summary)) {
      return false;
    }
    Summary summary = (Summary) obj;
    return Objects.equal(label_, summary.label_) && Objects.equal(polarity_, summary.polarity_)
        && Objects.equal(coverage_, summary.coverage_)
        && Objects.equal(overlaps_, summary.overlaps_)
        && Objects.equal(conflicts_, summary.conflicts_)
        && Objects.equal(correct_, summary.correct_)
        && Objects.equal(incorrect_, summary.incorrect_);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(label_, polarity_, coverage_, overlaps_, conflicts_, correct_,
        incorrect_);
  }

  /**
   * The name of the considered LF.
   *
   * @return the LF's name
   */
  @Generated
  public String label() {
    return label_;
  }

  /**
   * Polarity: The set of unique labels this LF outputs (excluding abstains)
   *
   * @return polarity
   */
  @Generated
  public Set<String> polarity() {
    return polarity_;
  }

  /**
   * Coverage: The fraction of the dataset this LF labels
   *
   * @return coverage
   */
  @Generated
  public double coverage() {
    return coverage_;
  }

  /**
   * Overlaps: The fraction of the dataset where this LF and at least one other LF label
   *
   * @return overlaps
   */
  @Generated
  public double overlaps() {
    return overlaps_;
  }

  /**
   * Conflicts: The fraction of the dataset where this LF and at least one other LF label and
   * disagree
   *
   * @return conflicts
   */
  @Generated
  public double conflicts() {
    return conflicts_;
  }

  /**
   * Correct: The number of data points this LF labels correctly (if gold labels are provided)
   *
   * @return correct
   */
  @Generated
  public int correct() {
    return correct_;
  }

  /**
   * Incorrect: The number of data points this LF labels incorrectly (if gold labels are provided)
   *
   * @return incorrect
   */
  @Generated
  public int incorrect() {
    return incorrect_;
  }

  // TODO : compute empirical accuracy
}
