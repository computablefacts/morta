package com.computablefacts.morta.snorkel.spacy;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
@JsonInclude(JsonInclude.Include.NON_NULL)
final public class AnnotatedText {

  private static final ObjectMapper mapper_ = new ObjectMapper();

  @JsonProperty(value = "meta", required = true)
  public final Meta meta_;
  @JsonProperty(value = "text", required = true)
  public final String text_;
  @JsonProperty(value = "_input_hash")
  public final String inputHash_;
  @JsonProperty(value = "_task_hash")
  public final String taskHash_;
  @JsonProperty(value = "tokens")
  public final List<Token> tokens_;
  @JsonProperty(value = "_view_id")
  public final String viewId_;
  @JsonProperty(value = "spans")
  public final List<Span> spans_;
  @JsonProperty(value = "answer")
  public final String answer_;
  @JsonProperty(value = "_timestamp")
  public final long timestamp_;

  public AnnotatedText(@JsonProperty(value = "meta") Meta meta,
      @JsonProperty(value = "text") String text) {
    this(meta, text, null, null, null, null, null, null, Instant.now().toEpochMilli());
  }

  @JsonCreator
  public AnnotatedText(@JsonProperty(value = "meta") Meta meta,
      @JsonProperty(value = "text") String text,
      @JsonProperty(value = "_input_hash") String inputHash,
      @JsonProperty(value = "_task_hash") String taskHash,
      @JsonProperty(value = "tokens") List<Token> tokens,
      @JsonProperty(value = "_view_id") String viewId,
      @JsonProperty(value = "spans") List<Span> spans,
      @JsonProperty(value = "answer") String answer,
      @JsonProperty(value = "_timestamp") long timestamp) {
    meta_ = meta;
    text_ = text;
    inputHash_ = inputHash;
    taskHash_ = taskHash;
    tokens_ = tokens;
    viewId_ = viewId;
    spans_ = spans;
    answer_ = answer;
    timestamp_ = timestamp;
  }
}
