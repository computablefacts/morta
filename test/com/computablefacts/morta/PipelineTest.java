package com.computablefacts.morta;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

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

    List<FeatureVector<Integer>> labels =
        Pipeline.on(Lists.newArrayList(1, 2, 3, 4, 5, 6)).labels(lfs).collect();

    Assert.assertEquals(goldLabels, labels);
  }

  @Test
  public void testSummariesWithoutGoldLabels() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);

    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", 1);
    lfLabels.put("KO", 0);

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);

    List<Summary> summaries =
        Pipeline.on(Lists.newArrayList(1, 2, 3, 4, 5, 6)).summaries(lfNames, lfLabels, lfs, null);
    Summary summaryIsDivisibleBy2 =
        new Summary("isDivisibleBy2", Sets.newHashSet("OK", "KO"), 1.0, 0.5, 0.5, -1, -1);
    Summary summaryIsDivisibleBy3 =
        new Summary("isDivisibleBy3", Sets.newHashSet("OK", "KO"), 1.0, 0.5, 0.5, -1, -1);

    Assert.assertEquals(lfNames.size(), summaries.size());
    Assert.assertEquals(summaryIsDivisibleBy2, summaries.get(0));
    Assert.assertEquals(summaryIsDivisibleBy3, summaries.get(1));
  }

  @Test
  public void testSummariesWithGoldLabels() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);
    lfNames.put("isDivisibleBy6", 2);

    // OK = isDivisibleBy2 AND isDivisibleBy3
    // KO = !isDivisibleBy2 OR !isDivisibleBy3
    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", 1);
    lfLabels.put("KO", 0);

    // instances = [1, 2, 3, 4, 5, 6]
    // goldLabels = ["KO", "KO", "KO", "KO", "KO", "OK"]
    List<Integer> goldLabels = Lists.newArrayList(0, 0, 0, 0, 0, 1);

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Summary> summaries = Pipeline.on(Lists.newArrayList(1, 2, 3, 4, 5, 6)).summaries(lfNames,
        lfLabels, lfs, goldLabels);
    Summary summaryIsDivisibleBy2 = new Summary("isDivisibleBy2", Sets.newHashSet("OK", "KO"), 1.0,
        0.6666666666666666, 0.5, 4, 2);
    Summary summaryIsDivisibleBy3 = new Summary("isDivisibleBy3", Sets.newHashSet("OK", "KO"), 1.0,
        0.8333333333333334, 0.5, 5, 1);
    Summary summaryIsDivisibleBy6 =
        new Summary("isDivisibleBy6", Sets.newHashSet("OK", "KO"), 1.0, 1.0, 0.5, 6, 0);

    Assert.assertEquals(lfNames.size(), summaries.size());
    Assert.assertEquals(summaryIsDivisibleBy2, summaries.get(0));
    Assert.assertEquals(summaryIsDivisibleBy3, summaries.get(1));
    Assert.assertEquals(summaryIsDivisibleBy6, summaries.get(2));
  }

  @Test
  public void testProbabilities() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);
    lfNames.put("isDivisibleBy6", 2);

    // OK = isDivisibleBy2 AND isDivisibleBy3
    // KO = !isDivisibleBy2 OR !isDivisibleBy3
    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", 1);
    lfLabels.put("KO", 0);

    // goldProbs = [[1.0, 0.0], [1.0, 0.0], [1.0, 0.0], [1.0, 0.0], [1.0, 0.0], [0.0, 1.0]]
    List<FeatureVector<Double>> goldProbs = Lists.newArrayList(
        FeatureVector.from(new double[] {1.0, 0.0}), FeatureVector.from(new double[] {1.0, 0.0}),
        FeatureVector.from(new double[] {1.0, 0.0}), FeatureVector.from(new double[] {1.0, 0.0}),
        FeatureVector.from(new double[] {1.0, 0.0}), FeatureVector.from(new double[] {0.0, 1.0}));

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    List<FeatureVector<Double>> probabilities =
        Pipeline.on(instances).probabilities(lfNames, lfLabels, lfs);

    Assert.assertEquals(instances.size(), probabilities.size());
    Assert.assertEquals(goldProbs, probabilities);
  }

  @Test
  public void testPredictions() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);
    lfNames.put("isDivisibleBy6", 2);

    // OK = isDivisibleBy2 AND isDivisibleBy3
    // KO = !isDivisibleBy2 OR !isDivisibleBy3
    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", 1);
    lfLabels.put("KO", 0);

    // instances = [1, 2, 3, 4, 5, 6]
    // goldLabels = ["KO", "KO", "KO", "KO", "KO", "OK"]
    List<Integer> goldLabels = Lists.newArrayList(0, 0, 0, 0, 0, 1);

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    List<Integer> predictions = Pipeline.on(instances).predictions(lfNames, lfLabels, lfs,
        MajorityLabelModel.eTieBreakPolicy.RANDOM);

    Assert.assertEquals(instances.size(), predictions.size());
    Assert.assertEquals(goldLabels, predictions);
  }

  @Test
  public void testAccuracy() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);
    lfNames.put("isDivisibleBy6", 2);

    // OK = isDivisibleBy2 AND isDivisibleBy3
    // KO = !isDivisibleBy2 OR !isDivisibleBy3
    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", 1);
    lfLabels.put("KO", 0);

    // instances = [1, 2, 3, 4, 5, 6]
    // goldLabels = ["KO", "KO", "KO", "KO", "KO", "OK"]
    List<Integer> goldLabels = Lists.newArrayList(0, 0, 0, 0, 0, 1);

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    List<Integer> accuracy = Pipeline.on(instances).accuracy(lfNames, lfLabels, lfs,
        MajorityLabelModel.eTieBreakPolicy.RANDOM, goldLabels);

    Assert.assertEquals(2, accuracy.size());
    Assert.assertEquals((Integer) 6, accuracy.get(0));
    Assert.assertEquals((Integer) 0, accuracy.get(1));
  }
}
