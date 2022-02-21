package com.computablefacts.morta.snorkel.spacy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

  @JsonCreator
  public Meta(@JsonProperty(value = "source") String source,
      @JsonProperty(value = "expected_label") String expectedLabel,
      @JsonProperty(value = "expected_answer") String expectedAnswer) {
    source_ = source;
    expectedLabel_ = expectedLabel;
    expectedAnswer_ = expectedAnswer;
  }
}
