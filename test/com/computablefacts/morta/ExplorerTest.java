package com.computablefacts.morta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

import smile.stat.hypothesis.CorTest;

public class ExplorerTest {

  @Test
  public void testLabelingFunctionsCorrelationsPearson() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);
    lfNames.put("isDivisibleBy6", 2);

    List<LabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    Table<String, String, CorTest> correlations = Pipeline.on(instances)
        .labelingFunctionsCorrelations(lfNames, lfs, Explorer.eCorrelation.PEARSON);

    Assert.assertEquals(9, correlations.size());
    Assert.assertEquals(1.0, correlations.get("isDivisibleBy2", "isDivisibleBy2").cor, 0.000001);
    Assert.assertEquals(1.0, correlations.get("isDivisibleBy3", "isDivisibleBy3").cor, 0.000001);
    Assert.assertEquals(1.0, correlations.get("isDivisibleBy6", "isDivisibleBy6").cor, 0.000001);
    Assert.assertEquals(correlations.get("isDivisibleBy3", "isDivisibleBy2").cor,
        correlations.get("isDivisibleBy2", "isDivisibleBy3").cor, 0.000001);
    Assert.assertEquals(correlations.get("isDivisibleBy6", "isDivisibleBy2").cor,
        correlations.get("isDivisibleBy2", "isDivisibleBy6").cor, 0.000001);
    Assert.assertEquals(correlations.get("isDivisibleBy6", "isDivisibleBy3").cor,
        correlations.get("isDivisibleBy3", "isDivisibleBy6").cor, 0.000001);
  }

  @Test
  public void testLabelingFunctionsCorrelationsSpearman() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);
    lfNames.put("isDivisibleBy6", 2);

    List<LabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    Table<String, String, CorTest> correlations = Pipeline.on(instances)
        .labelingFunctionsCorrelations(lfNames, lfs, Explorer.eCorrelation.SPEARMAN);

    Assert.assertEquals(9, correlations.size());
    Assert.assertEquals(1.0, correlations.get("isDivisibleBy2", "isDivisibleBy2").cor, 0.000001);
    Assert.assertEquals(1.0, correlations.get("isDivisibleBy3", "isDivisibleBy3").cor, 0.000001);
    Assert.assertEquals(1.0, correlations.get("isDivisibleBy6", "isDivisibleBy6").cor, 0.000001);
    Assert.assertEquals(correlations.get("isDivisibleBy3", "isDivisibleBy2").cor,
        correlations.get("isDivisibleBy2", "isDivisibleBy3").cor, 0.000001);
    Assert.assertEquals(correlations.get("isDivisibleBy6", "isDivisibleBy2").cor,
        correlations.get("isDivisibleBy2", "isDivisibleBy6").cor, 0.000001);
    Assert.assertEquals(correlations.get("isDivisibleBy6", "isDivisibleBy3").cor,
        correlations.get("isDivisibleBy3", "isDivisibleBy6").cor, 0.000001);
  }

  @Test
  public void testLabelingFunctionsCorrelationsKendall() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);
    lfNames.put("isDivisibleBy6", 2);

    List<LabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    Table<String, String, CorTest> correlations = Pipeline.on(instances)
        .labelingFunctionsCorrelations(lfNames, lfs, Explorer.eCorrelation.KENDALL);

    Assert.assertEquals(9, correlations.size());
    Assert.assertEquals(1.0, correlations.get("isDivisibleBy2", "isDivisibleBy2").cor, 0.000001);
    Assert.assertEquals(1.0, correlations.get("isDivisibleBy3", "isDivisibleBy3").cor, 0.000001);
    Assert.assertEquals(1.0, correlations.get("isDivisibleBy6", "isDivisibleBy6").cor, 0.000001);
    Assert.assertEquals(correlations.get("isDivisibleBy3", "isDivisibleBy2").cor,
        correlations.get("isDivisibleBy2", "isDivisibleBy3").cor, 0.000001);
    Assert.assertEquals(correlations.get("isDivisibleBy6", "isDivisibleBy2").cor,
        correlations.get("isDivisibleBy2", "isDivisibleBy6").cor, 0.000001);
    Assert.assertEquals(correlations.get("isDivisibleBy6", "isDivisibleBy3").cor,
        correlations.get("isDivisibleBy3", "isDivisibleBy6").cor, 0.000001);
  }

  @Test
  public void testExplore() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);
    lfNames.put("isDivisibleBy6", 2);

    List<LabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    List<Integer> goldLabels = Lists.newArrayList(0, 0, 0, 0, 0, 1);

    Table<String, Explorer.eStatus, List<Map.Entry<Integer, FeatureVector<Integer>>>> table =
        Pipeline.on(instances).explore(lfNames, lfs, goldLabels);

    Assert.assertEquals(5, table.size());

    Assert.assertEquals(Sets.newHashSet("isDivisibleBy2", "isDivisibleBy3", "isDivisibleBy6"),
        table.rowKeySet());
    Assert.assertEquals(Sets.newHashSet(Explorer.eStatus.CORRECT, Explorer.eStatus.INCORRECT),
        table.columnKeySet());

    Map<String, List<Map.Entry<Integer, FeatureVector<Integer>>>> correct =
        table.column(Explorer.eStatus.CORRECT);
    Map<String, List<Map.Entry<Integer, FeatureVector<Integer>>>> correctAbstain =
        table.column(Explorer.eStatus.CORRECT_ABSTAIN);
    Map<String, List<Map.Entry<Integer, FeatureVector<Integer>>>> incorrect =
        table.column(Explorer.eStatus.INCORRECT);
    Map<String, List<Map.Entry<Integer, FeatureVector<Integer>>>> incorrectAbstain =
        table.column(Explorer.eStatus.INCORRECT_ABSTAIN);

    Assert.assertTrue(correctAbstain.isEmpty());
    Assert.assertTrue(incorrectAbstain.isEmpty());

    // expected = [0, 0, 0, 0, 0, 1] vs actual = [0, 1, 0, 1, 0, 1]
    Assert.assertEquals(4, correct.get("isDivisibleBy2").size());
    Assert.assertEquals(2, incorrect.get("isDivisibleBy2").size());

    // expected = [0, 0, 0, 0, 0, 1] vs actual = [0, 0, 1, 0, 0, 1]
    Assert.assertEquals(5, correct.get("isDivisibleBy3").size());
    Assert.assertEquals(1, incorrect.get("isDivisibleBy3").size());

    // expected = [0, 0, 0, 0, 0, 1] vs actual = [0, 0, 0, 0, 0, 1]
    Assert.assertEquals(6, correct.get("isDivisibleBy6").size());
    Assert.assertEquals(0, incorrect.getOrDefault("isDivisibleBy6", new ArrayList<>()).size());
  }
}
