package com.computablefacts.morta.prodigy;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
@JsonInclude(JsonInclude.Include.NON_NULL)
final public class Meta {

  @JsonProperty(value = "source", required = true)
  public final String source_;
  @JsonProperty(value = "expected_label", required = true)
  public final String expectedLabel_;
  @JsonProperty(value = "expected_answer", required = true)
  public final String expectedAnswer_;
  @JsonProperty(value = "matched_keywords")
  public final String matchedKeywords_;

  public Meta(@JsonProperty(value = "source") String source,
      @JsonProperty(value = "expected_label") String expectedLabel,
      @JsonProperty(value = "expected_answer") String expectedAnswer) {
    this(source, expectedLabel, expectedAnswer, null);
  }

  @JsonCreator
  public Meta(@JsonProperty(value = "source") String source,
      @JsonProperty(value = "expected_label") String expectedLabel,
      @JsonProperty(value = "expected_answer") String expectedAnswer,
      @JsonProperty(value = "matched_keywords") String matchedKeywords) {
    source_ = source;
    expectedLabel_ = expectedLabel;
    expectedAnswer_ = expectedAnswer;
    matchedKeywords_ = matchedKeywords;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Meta)) {
      return false;
    }
    Meta meta = (Meta) o;
    return Objects.equals(source_, meta.source_)
        && Objects.equals(expectedLabel_, meta.expectedLabel_)
        && Objects.equals(expectedAnswer_, meta.expectedAnswer_)
        && Objects.equals(matchedKeywords_, meta.matchedKeywords_);
  }

  @Override
  public int hashCode() {
    return Objects.hash(source_, expectedLabel_, expectedAnswer_, matchedKeywords_);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("source", source_)
        .add("expected_label", expectedLabel_).add("expected_answer", expectedAnswer_)
        .add("matched_keyword", matchedKeywords_).omitNullValues().toString();
  }
}
