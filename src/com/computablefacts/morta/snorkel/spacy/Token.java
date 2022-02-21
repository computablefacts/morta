package com.computablefacts.morta.snorkel.spacy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
@JsonInclude(JsonInclude.Include.NON_NULL)
final public class Token {

  @JsonProperty(value = "text", required = true)
  public final String text_;
  @JsonProperty(value = "start", required = true)
  public final int start_;
  @JsonProperty(value = "end", required = true)
  public final int end_;
  @JsonProperty(value = "id", required = true)
  public final int id_;
  @JsonProperty(value = "ws", required = true)
  public final boolean ws_;

  @JsonCreator
  public Token(@JsonProperty(value = "text") String text, @JsonProperty(value = "start") int start,
      @JsonProperty(value = "end") int end, @JsonProperty(value = "id") int id,
      @JsonProperty(value = "ws") boolean ws) {
    text_ = text;
    start_ = start;
    end_ = end;
    id_ = id;
    ws_ = ws;
  }
}
