package com.computablefacts.morta.snorkel;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.computablefacts.morta.snorkel.Pipeline;
import org.junit.Assert;
import org.junit.Test;

import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.morta.snorkel.ILabelingFunction;
import com.google.common.collect.Lists;

public class PipelineTest {

  @Test
  public void testSlice() {

    List<Integer> evens =
        Pipeline.on(Lists.newArrayList(1, 2, 3, 4, 5, 6)).slice(x -> x % 2 == 0).collect();

    Assert.assertEquals(Lists.newArrayList(2, 4, 6), evens);
  }

  @Test
  public void testTransform() {

    List<String> strings = Pipeline.on(Lists.newArrayList(1, 2, 3, 4, 5, 6))
        .transform(x -> Integer.toString(x, 10)).collect();

    Assert.assertEquals(Lists.newArrayList("1", "2", "3", "4", "5", "6"), strings);
  }

  @Test
  public void testLabel() {

    FeatureVector<Integer> f1 = new FeatureVector<>(2, 0);
    f1.set(0, 0); // 1 mod 2 == 1
    f1.set(1, 0); // 1 mod 3 == 1

    FeatureVector<Integer> f2 = new FeatureVector<>(2, 0);
    f2.set(0, 1); // 2 mod 2 == 0
    f2.set(1, 0); // 2 mod 3 == 1

    FeatureVector<Integer> f3 = new FeatureVector<>(2, 0);
    f3.set(0, 0); // 3 mod 2 == 1
    f3.set(1, 1); // 3 mod 3 == 0

    FeatureVector<Integer> f4 = new FeatureVector<>(2, 0);
    f4.set(0, 1); // 4 mod 2 == 0
    f4.set(1, 0); // 4 mod 3 == 1

    FeatureVector<Integer> f5 = new FeatureVector<>(2, 0);
    f5.set(0, 0); // 5 mod 2 == 1
    f5.set(1, 0); // 5 mod 3 == 2

    FeatureVector<Integer> f6 = new FeatureVector<>(2, 0);
    f6.set(0, 1); // 6 mod 2 == 0
    f6.set(1, 1); // 6 mod 3 == 0

    List<Map.Entry<Integer, FeatureVector<Integer>>> goldLabels = Lists.newArrayList(
        new AbstractMap.SimpleEntry<>(1, f1), new AbstractMap.SimpleEntry<>(2, f2),
        new AbstractMap.SimpleEntry<>(3, f3), new AbstractMap.SimpleEntry<>(4, f4),
        new AbstractMap.SimpleEntry<>(5, f5), new AbstractMap.SimpleEntry<>(6, f6));

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);

    List<Map.Entry<Integer, FeatureVector<Integer>>> labels =
        Pipeline.on(Lists.newArrayList(1, 2, 3, 4, 5, 6)).label(lfs).collect();

    Assert.assertEquals(goldLabels, labels);
  }

  @Test
  public void testLabels() {

    FeatureVector<Integer> f1 = new FeatureVector<>(2, 0);
    f1.set(0, 0); // 1 mod 2 == 1
    f1.set(1, 0); // 1 mod 3 == 1

    FeatureVector<Integer> f2 = new FeatureVector<>(2, 0);
    f2.set(0, 1); // 2 mod 2 == 0
    f2.set(1, 0); // 2 mod 3 == 1

    FeatureVector<Integer> f3 = new FeatureVector<>(2, 0);
    f3.set(0, 0); // 3 mod 2 == 1
    f3.set(1, 1); // 3 mod 3 == 0

    FeatureVector<Integer> f4 = new FeatureVector<>(2, 0);
    f4.set(0, 1); // 4 mod 2 == 0
    f4.set(1, 0); // 4 mod 3 == 1

    FeatureVector<Integer> f5 = new FeatureVector<>(2, 0);
    f5.set(0, 0); // 5 mod 2 == 1
    f5.set(1, 0); // 5 mod 3 == 2

    FeatureVector<Integer> f6 = new FeatureVector<>(2, 0);
    f6.set(0, 1); // 6 mod 2 == 0
    f6.set(1, 1); // 6 mod 3 == 0

    List<FeatureVector<Integer>> goldLabels = Lists.newArrayList(f1, f2, f3, f4, f5, f6);

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);

    List<FeatureVector<Integer>> labels = Pipeline.on(Lists.newArrayList(1, 2, 3, 4, 5, 6))
        .label(lfs).transform(Map.Entry::getValue).collect();

    Assert.assertEquals(goldLabels, labels);
  }
}
