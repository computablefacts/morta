package com.computablefacts.morta.spacy;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
@JsonInclude(JsonInclude.Include.NON_NULL)
final public class Span {

  @JsonProperty(value = "start", required = true)
  public final int start_;
  @JsonProperty(value = "end", required = true)
  public final int end_;
  @JsonProperty(value = "token_start")
  public final Integer tokenStart_;
  @JsonProperty(value = "token_end")
  public final Integer tokenEnd_;
  @JsonProperty(value = "label", required = true)
  public final String label_;

  @JsonCreator
  public Span(@JsonProperty(value = "start") int start, @JsonProperty(value = "end") int end,
      @JsonProperty(value = "token_start") Integer tokenStart,
      @JsonProperty(value = "token_end") Integer tokenEnd,
      @JsonProperty(value = "label") String label) {
    start_ = start;
    end_ = end;
    tokenStart_ = tokenStart;
    tokenEnd_ = tokenEnd;
    label_ = label;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Span)) {
      return false;
    }
    Span span = (Span) o;
    return Objects.equals(start_, span.start_) && Objects.equals(end_, span.end_)
        && Objects.equals(tokenStart_, span.tokenStart_)
        && Objects.equals(tokenEnd_, span.tokenEnd_) && Objects.equals(label_, span.label_);
  }

  @Override
  public int hashCode() {
    return Objects.hash(start_, end_, tokenStart_, tokenEnd_, label_);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("start", start_).add("end", end_)
        .add("token_start", tokenStart_).add("token_end", tokenEnd_).add("label", label_)
        .omitNullValues().toString();
  }
}
