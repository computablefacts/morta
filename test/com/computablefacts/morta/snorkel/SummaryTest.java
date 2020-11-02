package com.computablefacts.morta.snorkel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.computablefacts.morta.Pipeline;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

import nl.jqno.equalsverifier.EqualsVerifier;
import smile.stat.hypothesis.CorTest;

public class SummaryTest {

  @Test
  public void testEqualsAndHashcode() {
    EqualsVerifier.forClass(Summary.class).verify();
  }

  @Test
  public void testLabelingFunctionsCorrelationsPearson() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);
    lfNames.put("isDivisibleBy6", 2);

    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", 1);
    lfLabels.put("KO", 0);

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    Table<String, String, CorTest> correlations = Summary.labelingFunctionsCorrelations(lfNames,
        lfLabels, Pipeline.on(instances).label(lfs).collect(), Summary.eCorrelation.PEARSON);

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

    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", 1);
    lfLabels.put("KO", 0);

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    Table<String, String, CorTest> correlations = Summary.labelingFunctionsCorrelations(lfNames,
        lfLabels, Pipeline.on(instances).label(lfs).collect(), Summary.eCorrelation.SPEARMAN);

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

    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", 1);
    lfLabels.put("KO", 0);

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    Table<String, String, CorTest> correlations = Summary.labelingFunctionsCorrelations(lfNames,
        lfLabels, Pipeline.on(instances).label(lfs).collect(), Summary.eCorrelation.KENDALL);

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

    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", 1);
    lfLabels.put("KO", 0);

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    List<Integer> goldLabels = Lists.newArrayList(0, 0, 0, 0, 0, 1);

    Table<String, Summary.eStatus, List<Map.Entry<Integer, FeatureVector<Integer>>>> table =
        Summary.explore(lfNames, lfLabels, Pipeline.on(instances).label(lfs).collect(), goldLabels);

    Assert.assertEquals(5, table.size());

    Assert.assertEquals(Sets.newHashSet("isDivisibleBy2", "isDivisibleBy3", "isDivisibleBy6"),
        table.rowKeySet());
    Assert.assertEquals(Sets.newHashSet(Summary.eStatus.CORRECT, Summary.eStatus.INCORRECT),
        table.columnKeySet());

    Map<String, List<Map.Entry<Integer, FeatureVector<Integer>>>> correct =
        table.column(Summary.eStatus.CORRECT);
    Map<String, List<Map.Entry<Integer, FeatureVector<Integer>>>> correctAbstain =
        table.column(Summary.eStatus.CORRECT_ABSTAIN);
    Map<String, List<Map.Entry<Integer, FeatureVector<Integer>>>> incorrect =
        table.column(Summary.eStatus.INCORRECT);
    Map<String, List<Map.Entry<Integer, FeatureVector<Integer>>>> incorrectAbstain =
        table.column(Summary.eStatus.INCORRECT_ABSTAIN);

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

  @Test
  public void testSummarizeWithoutGoldLabels() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);

    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", 1);
    lfLabels.put("KO", 0);

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    List<Summary> summaries =
        Summary.summarize(lfNames, lfLabels, Pipeline.on(instances).label(lfs).collect(), null);

    Summary summaryIsDivisibleBy2 =
        new Summary("isDivisibleBy2", Sets.newHashSet("OK", "KO"), 1.0, 0.5, 0.5, -1, -1);
    Summary summaryIsDivisibleBy3 =
        new Summary("isDivisibleBy3", Sets.newHashSet("OK", "KO"), 1.0, 0.5, 0.5, -1, -1);

    Assert.assertEquals(lfNames.size(), summaries.size());
    Assert.assertEquals(summaryIsDivisibleBy2, summaries.get(0));
    Assert.assertEquals(summaryIsDivisibleBy3, summaries.get(1));
  }

  @Test
  public void testSummarizeWithGoldLabels() {

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

    List<Summary> summaries = Summary.summarize(lfNames, lfLabels,
        Pipeline.on(Lists.newArrayList(1, 2, 3, 4, 5, 6)).label(lfs).collect(), goldLabels);
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
}
