package com.computablefacts.morta.snorkel.spacy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
@JsonInclude(JsonInclude.Include.NON_NULL)
final public class Span {

  @JsonProperty(value = "start", required = true)
  public final int start_;
  @JsonProperty(value = "end", required = true)
  public final int end_;
  @JsonProperty(value = "token_start", required = true)
  public final int tokenStart_;
  @JsonProperty(value = "token_end", required = true)
  public final int tokenEnd_;
  @JsonProperty(value = "label", required = true)
  public final String label_;

  @JsonCreator
  public Span(@JsonProperty(value = "start") int start, @JsonProperty(value = "end") int end,
      @JsonProperty(value = "token_start") int tokenStart,
      @JsonProperty(value = "token_end") int tokenEnd,
      @JsonProperty(value = "label") String label) {
    start_ = start;
    end_ = end;
    tokenStart_ = tokenStart;
    tokenEnd_ = tokenEnd;
    label_ = label;
  }
}
