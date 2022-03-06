package com.computablefacts.morta;

import static com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction.KO;
import static com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction.OK;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.computablefacts.asterix.View;
import com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction;
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
    lfLabels.put("OK", OK);
    lfLabels.put("KO", KO);

    List<AbstractLabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy2") {

      @Override
      public Integer apply(Integer x) {
        return x % 2 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy3") {

      @Override
      public Integer apply(Integer x) {
        return x % 3 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy6") {

      @Override
      public Integer apply(Integer x) {
        return x % 6 == 0 ? OK : KO;
      }
    });

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    Table<String, String, CorTest> correlations =
        Summary.labelingFunctionsCorrelations(lfNames, lfLabels,
            View.of(instances).map(Helpers.label(lfs)).toList(), Summary.eCorrelation.PEARSON);

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
    lfLabels.put("OK", OK);
    lfLabels.put("KO", KO);

    List<AbstractLabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy2") {

      @Override
      public Integer apply(Integer x) {
        return x % 2 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy3") {

      @Override
      public Integer apply(Integer x) {
        return x % 3 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy6") {

      @Override
      public Integer apply(Integer x) {
        return x % 6 == 0 ? OK : KO;
      }
    });

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    Table<String, String, CorTest> correlations =
        Summary.labelingFunctionsCorrelations(lfNames, lfLabels,
            View.of(instances).map(Helpers.label(lfs)).toList(), Summary.eCorrelation.SPEARMAN);

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
    lfLabels.put("OK", OK);
    lfLabels.put("KO", KO);

    List<AbstractLabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy2") {

      @Override
      public Integer apply(Integer x) {
        return x % 2 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy3") {

      @Override
      public Integer apply(Integer x) {
        return x % 3 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy6") {

      @Override
      public Integer apply(Integer x) {
        return x % 6 == 0 ? OK : KO;
      }
    });

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    Table<String, String, CorTest> correlations =
        Summary.labelingFunctionsCorrelations(lfNames, lfLabels,
            View.of(instances).map(Helpers.label(lfs)).toList(), Summary.eCorrelation.KENDALL);

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
    lfLabels.put("OK", OK);
    lfLabels.put("KO", KO);

    List<AbstractLabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy2") {

      @Override
      public Integer apply(Integer x) {
        return x % 2 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy3") {

      @Override
      public Integer apply(Integer x) {
        return x % 3 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy6") {

      @Override
      public Integer apply(Integer x) {
        return x % 6 == 0 ? OK : KO;
      }
    });

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    List<Integer> goldLabels = Lists.newArrayList(0, 0, 0, 0, 0, 1);

    Table<String, Summary.eStatus, List<Map.Entry<Integer, FeatureVector<Integer>>>> table =
        Summary.explore(lfNames, lfLabels, View.of(instances).map(Helpers.label(lfs)).toList(),
            goldLabels);

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
    lfLabels.put("OK", OK);
    lfLabels.put("KO", KO);

    List<AbstractLabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy2") {

      @Override
      public Integer apply(Integer x) {
        return x % 2 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy3") {

      @Override
      public Integer apply(Integer x) {
        return x % 3 == 0 ? OK : KO;
      }
    });

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    List<Summary> summaries = Summary.summarize(lfNames, lfLabels,
        View.of(instances).map(Helpers.label(lfs)).toList(), null);

    Summary summaryIsDivisibleBy2 = new Summary("isDivisibleBy2", Sets.newHashSet("OK", "KO"), 1.0,
        0.5, 0.5, -1, -1, -1, Sets.newHashSet("isDivisibleBy3"), Sets.newHashSet("isDivisibleBy3"));
    Summary summaryIsDivisibleBy3 = new Summary("isDivisibleBy3", Sets.newHashSet("OK", "KO"), 1.0,
        0.5, 0.5, -1, -1, -1, Sets.newHashSet("isDivisibleBy2"), Sets.newHashSet("isDivisibleBy2"));

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
    lfLabels.put("OK", OK);
    lfLabels.put("KO", KO);

    // instances = [1, 2, 3, 4, 5, 6]
    // goldLabels = ["KO", "KO", "KO", "KO", "KO", "OK"]
    List<Integer> goldLabels = Lists.newArrayList(0, 0, 0, 0, 0, 1);

    List<AbstractLabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy2") {

      @Override
      public Integer apply(Integer x) {
        return x % 2 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy3") {

      @Override
      public Integer apply(Integer x) {
        return x % 3 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<Integer>("isDivisibleBy6") {

      @Override
      public Integer apply(Integer x) {
        return x % 6 == 0 ? OK : KO;
      }
    });

    List<Summary> summaries = Summary.summarize(lfNames, lfLabels,
        View.of(Lists.newArrayList(1, 2, 3, 4, 5, 6)).map(Helpers.label(lfs)).toList(), goldLabels);
    Summary summaryIsDivisibleBy2 = new Summary("isDivisibleBy2", Sets.newHashSet("OK", "KO"), 1.0,
        0.6666666666666666, 0.5, 4, 2, 0, Sets.newHashSet("isDivisibleBy6", "isDivisibleBy3"),
        Sets.newHashSet("isDivisibleBy6", "isDivisibleBy3"));
    Summary summaryIsDivisibleBy3 = new Summary("isDivisibleBy3", Sets.newHashSet("OK", "KO"), 1.0,
        0.8333333333333334, 0.5, 5, 1, 0, Sets.newHashSet("isDivisibleBy6", "isDivisibleBy2"),
        Sets.newHashSet("isDivisibleBy6", "isDivisibleBy2"));
    Summary summaryIsDivisibleBy6 = new Summary("isDivisibleBy6", Sets.newHashSet("OK", "KO"), 1.0,
        1.0, 0.5, 6, 0, 0, Sets.newHashSet("isDivisibleBy3", "isDivisibleBy2"),
        Sets.newHashSet("isDivisibleBy3", "isDivisibleBy2"));

    Assert.assertEquals(lfNames.size(), summaries.size());
    Assert.assertEquals(summaryIsDivisibleBy2, summaries.get(0));
    Assert.assertEquals(summaryIsDivisibleBy3, summaries.get(1));
    Assert.assertEquals(summaryIsDivisibleBy6, summaries.get(2));
  }
}
