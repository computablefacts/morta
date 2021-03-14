package com.computablefacts.morta.yaml.patterns;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class KeyValue {

  @JsonProperty("key")
  String key_;

  @JsonProperty("value")
  String value_;

  KeyValue() {}
}
