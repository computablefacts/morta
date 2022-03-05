package com.computablefacts.morta.nextgen2;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.validation.constraints.NotNull;

import com.computablefacts.asterix.View;
import com.computablefacts.asterix.codecs.JsonCodec;
import com.computablefacts.asterix.console.AsciiProgressBar;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.spacy.AnnotatedText;
import com.computablefacts.morta.spacy.Meta;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GoldLabelOfString implements IGoldLabel<String> {

  @JsonProperty(value = "id", required = true)
  private final String id_;
  @JsonProperty(value = "label", required = true)
  private final String label_;
  @JsonProperty(value = "data", required = true)
  private final String data_;
  @JsonProperty(value = "is_true_negative", required = true)
  private final boolean isTrueNegative_;
  @JsonProperty(value = "is_true_positive", required = true)
  private final boolean isTruePositive_;
  @JsonProperty(value = "is_false_negative", required = true)
  private final boolean isFalseNegative_;
  @JsonProperty(value = "is_false_positive", required = true)
  private final boolean isFalsePositive_;

  public GoldLabelOfString(IGoldLabel<String> goldLabel) {
    this(goldLabel.id(), goldLabel.label(), goldLabel.data(), goldLabel.isTrueNegative(),
        goldLabel.isTruePositive(), goldLabel.isFalseNegative(), goldLabel.isFalsePositive());
  }

  public GoldLabelOfString(Map<String, Object> goldLabel) {
    this((String) goldLabel.get("id"), (String) goldLabel.get("label"),
        (String) goldLabel.get("data"), (Boolean) goldLabel.get("is_true_negative"),
        (Boolean) goldLabel.get("is_true_positive"), (Boolean) goldLabel.get("is_false_negative"),
        (Boolean) goldLabel.get("is_false_positive"));
  }

  @JsonCreator
  public GoldLabelOfString(@JsonProperty(value = "id") String id,
      @JsonProperty(value = "label") String label, @JsonProperty(value = "data") String data,
      @JsonProperty(value = "is_true_negative") boolean isTrueNegative,
      @JsonProperty(value = "is_true_positive") boolean isTruePositive,
      @JsonProperty(value = "is_false_negative") boolean isFalseNegative,
      @JsonProperty(value = "is_false_positive") boolean isFalsePositive) {

    Preconditions.checkArgument(!Strings.isNullOrEmpty(id), "id should neither be null nor empty");
    Preconditions.checkArgument(!Strings.isNullOrEmpty(label),
        "label should neither be null nor empty");
    Preconditions.checkArgument(!Strings.isNullOrEmpty(data),
        "data should neither be null nor empty");

    id_ = id;
    label_ = label;
    data_ = data;
    isTrueNegative_ = isTrueNegative;
    isTruePositive_ = isTruePositive;
    isFalseNegative_ = isFalseNegative;
    isFalsePositive_ = isFalsePositive;

    int tp = isTruePositive ? 1 : 0;
    int tn = isTrueNegative ? 1 : 0;
    int fp = isFalsePositive ? 1 : 0;
    int fn = isFalseNegative ? 1 : 0;

    Preconditions.checkState(tp + tn + fp + fn == 1,
        "inconsistent state reached for gold label : (%s, %s)", id(), label());
  }

  /**
   * Load gold labels from a gzipped JSONL file.
   *
   * @param file the input file.
   * @param label the specific gold labels to load. If {@code label} is set to {@code null}, all
   *        gold labels will be loaded.
   * @param withProgressBar true iif a progress bar should be displayed, false otherwise.
   * @return a set of gold labels.
   */
  public static Set<IGoldLabel<String>> load(File file, String label, boolean withProgressBar) {

    Preconditions.checkNotNull(file, "file should not be null");
    Preconditions.checkArgument(file.exists(), "file file does not exist : %s", file);

    AsciiProgressBar.ProgressBar progressBar = withProgressBar ? AsciiProgressBar.create() : null;
    AtomicInteger nbGoldLabels = new AtomicInteger(
        withProgressBar ? View.of(file, true).reduce(0, (carry, row) -> carry + 1) : 0);

    return View.of(file, true).index()
        .filter(row -> !Strings.isNullOrEmpty(row.getValue()) /* remove empty rows */).map(row -> {
          if (progressBar != null) {
            progressBar.update(row.getKey(), nbGoldLabels.get());
          }
          return (IGoldLabel<String>) new GoldLabelOfString(JsonCodec.asObject(row.getValue()));
        }).filter(goldLabel -> label == null || label.equals(goldLabel.label())).toSet();
  }

  /**
   * Save gold labels to a gzipped JSONL file.
   *
   * @param file the output file.
   * @param goldLabels the set of gold labels to save.
   * @return true iif the gold labels have been written to the file, false otherwise.
   */
  @CanIgnoreReturnValue
  public static boolean save(File file, Collection<? extends IGoldLabel<String>> goldLabels) {

    Preconditions.checkNotNull(file, "file should not be null");
    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    if (!file.exists()) {
      if (!goldLabels.isEmpty()) {
        View.of(goldLabels).toFile(JsonCodec::asString, file, false, true);
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof IGoldLabel)) {
      return false;
    }
    IGoldLabel<?> gl = (IGoldLabel<?>) o;
    return Objects.equals(id(), gl.id()) && Objects.equals(label(), gl.label())
        && Objects.equals(data(), gl.data()) && Objects.equals(snippet(), gl.snippet())
        && Objects.equals(isTrueNegative(), gl.isTrueNegative())
        && Objects.equals(isTruePositive(), gl.isTruePositive())
        && Objects.equals(isFalseNegative(), gl.isFalseNegative())
        && Objects.equals(isFalsePositive(), gl.isFalsePositive());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id(), label(), data(), snippet(), isTrueNegative(), isTruePositive(),
        isFalseNegative(), isFalsePositive());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("id", id()).add("label", label())
        .add("data", data()).add("snippet", snippet()).add("is_true_negative", isTrueNegative())
        .add("is_true_positive", isTruePositive()).add("is_false_negative", isFalseNegative())
        .add("is_false_positive", isFalsePositive()).omitNullValues().toString();
  }

  @Override
  public @NotNull String id() {
    return id_;
  }

  @Override
  public @NotNull String label() {
    return label_;
  }

  @Override
  public @NotNull String data() {
    return data_;
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

  /**
   * Output the current gold label in <a href="https://prodi.gy">Prodigy</a> data format for
   * <a href="https://prodi.gy/docs/text-classification">text classification</a> or
   * <a href="https://prodi.gy/docs/span-categorization">span categorization</a>.
   *
   * @return an {@link AnnotatedText}.
   */
  public AnnotatedText asProdigyAnnotation() {
    boolean accept = isTruePositive() || isFalseNegative();
    Meta meta = new Meta(id(), label(), accept ? "accept" : "reject");
    return new AnnotatedText(meta, data());
  }
}
