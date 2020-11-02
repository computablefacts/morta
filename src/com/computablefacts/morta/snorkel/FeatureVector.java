package com.computablefacts.morta.snorkel;

import java.util.Vector;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class FeatureVector<N extends Number> extends Vector<N> {

  public FeatureVector(int initialCapacity, N defaultValue) {

    super(initialCapacity);

    for (int i = 0; i < initialCapacity; i++) {
      add(defaultValue);
    }
  }

  public FeatureVector(int initialCapacity) {
    super(initialCapacity);
  }

  public static FeatureVector<Double> from(double[] array) {

    Preconditions.checkNotNull(array, "array should not be null");

    FeatureVector<Double> vector = new FeatureVector<>(array.length);

    for (int i = 0; i < array.length; i++) {
      vector.add(array[i]);
    }
    return vector;
  }

  public static FeatureVector<Integer> from(int[] array) {

    Preconditions.checkNotNull(array, "array should not be null");

    FeatureVector<Integer> vector = new FeatureVector<>(array.length);

    for (int i = 0; i < array.length; i++) {
      vector.add(array[i]);
    }
    return vector;
  }

  public double[] toDoubleArray() {

    double[] array = new double[size()];

    for (int i = 0; i < size(); i++) {
      array[i] = get(i).doubleValue();
    }
    return array;
  }

  public int[] toIntArray() {

    int[] array = new int[size()];

    for (int i = 0; i < size(); i++) {
      array[i] = get(i).intValue();
    }
    return array;
  }
}
