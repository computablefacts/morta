package com.computablefacts.morta.prodigy;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
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

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Token)) {
      return false;
    }
    Token token = (Token) o;
    return Objects.equals(text_, token.text_) && Objects.equals(start_, token.start_)
        && Objects.equals(end_, token.end_) && Objects.equals(id_, token.id_)
        && Objects.equals(ws_, token.ws_);
  }

  @Override
  public int hashCode() {
    return Objects.hash(text_, start_, end_, id_, ws_);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("id", id_).add("text", text_).add("start", start_)
        .add("end", end_).add("ws", ws_).omitNullValues().toString();
  }
}
