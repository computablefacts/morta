package com.computablefacts.morta;

import java.util.Vector;

import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class FeatureVector<N extends Number> extends Vector<N> {

  public FeatureVector(int initialCapacity, N defaultValue) {

    super(initialCapacity);

    for (int i = 0; i < initialCapacity; i++) {
      add(defaultValue);
    }
  }

  public double[] asDoubleArray() {

    double[] array = new double[size()];

    for (int i = 0; i < size(); i++) {
      array[i] = get(i).doubleValue();
    }
    return array;
  }

  public int[] asIntArray() {

    int[] array = new int[size()];

    for (int i = 0; i < size(); i++) {
      array[i] = get(i).intValue();
    }
    return array;
  }
}
