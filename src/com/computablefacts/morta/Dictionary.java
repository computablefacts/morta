/*
 * Copyright (c) 2011-2020 MNCC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * @author http://www.mncc.fr
 */
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
    if (obj == null) {
      return false;
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
