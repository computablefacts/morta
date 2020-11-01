package com.computablefacts.morta;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.computablefacts.nona.Generated;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class Dictionary implements Map<String, Integer> {

  private final BiMap<String, Integer> dict_ = HashBiMap.create();

  public Dictionary() {}

  @Generated
  @Override
  public String toString() {
    return dict_.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof Dictionary)) {
      return false;
    }
    Dictionary vector = (Dictionary) obj;
    return Objects.equal(dict_, vector.dict_);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(dict_);
  }

  @Override
  public boolean isEmpty() {
    return dict_.isEmpty();
  }

  @Override
  public void clear() {
    dict_.clear();
  }

  @Override
  public Set<String> keySet() {
    return dict_.keySet();
  }

  @Override
  public Collection<Integer> values() {
    return dict_.values();
  }

  @Override
  public Set<Entry<String, Integer>> entrySet() {
    return dict_.entrySet();
  }

  @Override
  public int size() {
    return dict_.size();
  }

  @Override
  public boolean containsKey(Object value) {

    Preconditions.checkNotNull(value, "value should not be null");
    Preconditions.checkArgument(value instanceof String, "value should be an instance of a String");

    return dict_.containsKey(value);
  }

  @Override
  public boolean containsValue(Object value) {

    Preconditions.checkNotNull(value, "value should not be null");
    Preconditions.checkArgument(value instanceof Integer,
        "value should be an instance of a String");

    return dict_.containsValue(value);
  }

  @Override
  public Integer get(Object label) {

    Preconditions.checkNotNull(label, "label should not be null");
    Preconditions.checkArgument(label instanceof String, "label should be an instance of a String");

    return dict_.get(label);
  }

  @CanIgnoreReturnValue
  @Override
  public Integer put(String label, Integer id) {

    Preconditions.checkNotNull(label, "label should not be null");
    Preconditions.checkState(!dict_.containsKey(label), "label already in use for index %s", id);
    Preconditions.checkState(!dict_.containsValue(id), "id already in use for label %s", label);

    return dict_.put(label, id);
  }

  @CanIgnoreReturnValue
  @Override
  public Integer remove(Object label) {

    Preconditions.checkNotNull(label, "label should not be null");
    Preconditions.checkArgument(label instanceof String, "label should be an instance of a String");

    return dict_.remove(label);
  }

  @Override
  public void putAll(Map<? extends String, ? extends Integer> map) {

    Preconditions.checkNotNull(map, "map should not be null");

    map.forEach(this::put);
  }

  public int id(String label) {

    Preconditions.checkNotNull(label, "label should not be null");

    return dict_.get(label);
  }

  public String label(int id) {
    return dict_.inverse().get(id);
  }
}
