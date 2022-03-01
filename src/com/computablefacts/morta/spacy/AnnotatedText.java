package com.computablefacts.morta.spacy;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
@JsonInclude(JsonInclude.Include.NON_NULL)
final public class AnnotatedText {

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
  public final Long timestamp_;

  public AnnotatedText(@JsonProperty(value = "meta") Meta meta,
      @JsonProperty(value = "text") String text) {
    this(meta, text, null, null, null, null, null, null, null);
  }

  public AnnotatedText(@JsonProperty(value = "meta") Meta meta,
      @JsonProperty(value = "text") String text,
      @JsonProperty(value = "tokens") List<Token> tokens) {
    this(meta, text, null, null, tokens, null, null, null, null);
  }

  public AnnotatedText(@JsonProperty(value = "meta") Meta meta,
      @JsonProperty(value = "text") String text, @JsonProperty(value = "tokens") List<Token> tokens,
      @JsonProperty(value = "spans") List<Span> spans) {
    this(meta, text, null, null, tokens, null, spans, null, null);
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
      @JsonProperty(value = "_timestamp") Long timestamp) {
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

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AnnotatedText)) {
      return false;
    }
    AnnotatedText annotatedText = (AnnotatedText) o;
    return Objects.equals(meta_, annotatedText.meta_) && Objects.equals(text_, annotatedText.text_)
        && Objects.equals(tokens_, annotatedText.tokens_)
        && Objects.equals(spans_, annotatedText.spans_)
        && Objects.equals(answer_, annotatedText.answer_);
  }

  @Override
  public int hashCode() {
    return Objects.hash(meta_, text_, tokens_, spans_, answer_);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("meta", meta_).add("text", text_)
        .add("tokens", tokens_).add("spans", spans_).add("answer", answer_).omitNullValues()
        .toString();
  }
}
