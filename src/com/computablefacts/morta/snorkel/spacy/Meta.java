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

  @JsonCreator
  public Meta(@JsonProperty(value = "source") String source) {
    source_ = source;
  }
}
