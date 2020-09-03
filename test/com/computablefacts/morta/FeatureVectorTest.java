package com.computablefacts.morta;

import org.junit.Assert;
import org.junit.Test;

public class FeatureVectorTest {

  @Test
  public void testZeroVector() {

    FeatureVector<Double> vector = new FeatureVector<>(5, 0.0);

    Assert.assertEquals(5, vector.size());
    Assert.assertEquals((Double) 0.0, vector.get(0));
    Assert.assertEquals((Double) 0.0, vector.get(1));
    Assert.assertEquals((Double) 0.0, vector.get(2));
    Assert.assertEquals((Double) 0.0, vector.get(3));
    Assert.assertEquals((Double) 0.0, vector.get(4));
  }

  @Test
  public void testAsDoubleArray() {

    FeatureVector<Double> vector = new FeatureVector<>(5, 1.0);
    double[] array = vector.asDoubleArray();

    Assert.assertArrayEquals(new double[] {1.0, 1.0, 1.0, 1.0, 1.0}, array, 0.0);
  }

  @Test
  public void testAsIntArray() {

    FeatureVector<Double> vector = new FeatureVector<>(5, 1.0);
    int[] array = vector.asIntArray();

    Assert.assertArrayEquals(new int[] {1, 1, 1, 1, 1}, array);
  }
}
