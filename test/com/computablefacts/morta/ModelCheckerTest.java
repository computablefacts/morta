package com.computablefacts.morta;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

public class ModelCheckerTest {

  @Test
  public void testAccuracyAllOk() {

    List<Integer> predictions = Lists.newArrayList(1, 2, 3, 4, 5);
    List<Integer> goldLabels = Lists.newArrayList(1, 2, 3, 4, 5);

    List<Integer> accuracy = ModelChecker.accuracy(predictions, goldLabels);

    Assert.assertEquals(2, accuracy.size());
    Assert.assertEquals((Integer) 5, accuracy.get(0));
    Assert.assertEquals((Integer) 0, accuracy.get(1));
    Assert.assertEquals(5, accuracy.get(0) + accuracy.get(1));
  }

  @Test
  public void testAccuracySomeKo() {

    List<Integer> predictions = Lists.newArrayList(1, 2, 3, 4, 5);
    List<Integer> goldLabels = Lists.newArrayList(1, 4, 3, 8, 5);

    List<Integer> accuracy = ModelChecker.accuracy(predictions, goldLabels);

    Assert.assertEquals(2, accuracy.size());
    Assert.assertEquals((Integer) 3, accuracy.get(0));
    Assert.assertEquals((Integer) 2, accuracy.get(1));
    Assert.assertEquals(5, accuracy.get(0) + accuracy.get(1));
  }
}
