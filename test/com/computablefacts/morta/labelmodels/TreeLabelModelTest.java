package com.computablefacts.morta.labelmodels;

import static com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction.KO;
import static com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction.OK;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.morta.FeatureVector;
import com.computablefacts.morta.GoldLabelOfString;
import com.computablefacts.morta.IGoldLabel;
import com.computablefacts.morta.Summary;
import com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

import smile.stat.hypothesis.CorTest;

public class TreeLabelModelTest {

  @Test
  public void testLabelingFunctionsCorrelations() {

    Table<String, String, CorTest> matrix =
        labelModel().labelingFunctionsCorrelations(goldLabels(), Summary.eCorrelation.PEARSON);

    Assert.assertEquals(Sets.newHashSet("isDivisibleBy2", "isDivisibleBy3", "isDivisibleBy6"),
        matrix.rowKeySet());
    Assert.assertEquals(Sets.newHashSet("isDivisibleBy2", "isDivisibleBy3", "isDivisibleBy6"),
        matrix.columnKeySet());

    Assert.assertEquals(1.0, matrix.get("isDivisibleBy2", "isDivisibleBy2").cor, 0.01);
    Assert.assertEquals(0.0, matrix.get("isDivisibleBy2", "isDivisibleBy3").cor, 0.01);
    Assert.assertEquals(0.45, matrix.get("isDivisibleBy2", "isDivisibleBy6").cor, 0.01);

    Assert.assertEquals(0.0, matrix.get("isDivisibleBy3", "isDivisibleBy2").cor, 0.01);
    Assert.assertEquals(1.0, matrix.get("isDivisibleBy3", "isDivisibleBy3").cor, 0.01);
    Assert.assertEquals(0.63, matrix.get("isDivisibleBy3", "isDivisibleBy6").cor, 0.01);

    Assert.assertEquals(0.45, matrix.get("isDivisibleBy6", "isDivisibleBy2").cor, 0.01);
    Assert.assertEquals(0.63, matrix.get("isDivisibleBy6", "isDivisibleBy3").cor, 0.01);
    Assert.assertEquals(1.0, matrix.get("isDivisibleBy6", "isDivisibleBy6").cor, 0.01);
  }

  @Test
  public void testExplore() {

    Table<String, Summary.eStatus, List<Map.Entry<String, FeatureVector<Integer>>>> table =
        labelModel().explore(goldLabels());

    Assert.assertEquals(Sets.newHashSet("isDivisibleBy2", "isDivisibleBy3", "isDivisibleBy6"),
        table.rowKeySet());
    Assert.assertEquals(Sets.newHashSet(Summary.eStatus.CORRECT, Summary.eStatus.INCORRECT),
        table.columnKeySet());

    Assert.assertEquals(isDivisibleBy2Correct(),
        table.get("isDivisibleBy2", Summary.eStatus.CORRECT));
    Assert.assertEquals(isDivisibleBy2Incorrect(),
        table.get("isDivisibleBy2", Summary.eStatus.INCORRECT));

    Assert.assertEquals(isDivisibleBy3Correct(),
        table.get("isDivisibleBy3", Summary.eStatus.CORRECT));
    Assert.assertEquals(isDivisibleBy3Incorrect(),
        table.get("isDivisibleBy3", Summary.eStatus.INCORRECT));

    Assert.assertEquals(isDivisibleBy6Correct(),
        table.get("isDivisibleBy6", Summary.eStatus.CORRECT));
    Assert.assertEquals(isDivisibleBy6Incorrect(),
        table.get("isDivisibleBy6", Summary.eStatus.INCORRECT));
  }

  @Test
  public void testSummarize() {

    List<Summary> list = labelModel().summarize(goldLabels());

    Assert.assertEquals(summaries(), list);
  }

  @Test
  public void testPredict() {

    TreeLabelModel<String> labelModel = labelModel();
    labelModel.fit(goldLabels());

    List<Integer> list = labelModel
        .predict(goldLabels().stream().map(IGoldLabel::data).collect(Collectors.toList()));

    Assert.assertEquals(Lists.newArrayList(KO, KO, OK, KO, KO, OK, KO, KO, OK, KO, KO, OK), list);
  }

  @Test
  public void testConfusionMatrix() {

    TreeLabelModel<String> labelModel = labelModel();
    labelModel.fit(goldLabels());

    ConfusionMatrix matrix = labelModel.confusionMatrix(goldLabels());

    Assert.assertEquals(4, matrix.nbTruePositives());
    Assert.assertEquals(8, matrix.nbTrueNegatives());
    Assert.assertEquals(0, matrix.nbFalsePositives());
    Assert.assertEquals(0, matrix.nbFalseNegatives());
  }

  private TreeLabelModel<String> labelModel() {
    return new TreeLabelModel<>(lfs(), TreeLabelModel.eMetric.F1);
  }

  private List<AbstractLabelingFunction<String>> lfs() {

    List<AbstractLabelingFunction<String>> lfs = new ArrayList<>();
    lfs.add(new AbstractLabelingFunction<String>("isDivisibleBy2") {

      @Override
      public Integer apply(String s) {
        return Integer.parseInt(s, 10) % 2 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<String>("isDivisibleBy3") {

      @Override
      public Integer apply(String s) {
        return Integer.parseInt(s, 10) % 3 == 0 ? OK : KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<String>("isDivisibleBy6") {

      @Override
      public Integer apply(String s) {
        return Integer.parseInt(s, 10) % 6 == 0 ? OK : KO;
      }
    });

    return lfs;
  }

  private List<IGoldLabel<String>> goldLabels() {

    // OK = isDivisibleBy3
    // KO = !isDivisibleBy3
    return Lists.newArrayList(
        new GoldLabelOfString(Integer.toString(1, 10), "divisibleBy3", "1", false, false, false,
            true),
        new GoldLabelOfString(Integer.toString(2, 10), "divisibleBy3", "2", false, false, false,
            true),
        new GoldLabelOfString(Integer.toString(3, 10), "divisibleBy3", "3", false, true, false,
            false),
        new GoldLabelOfString(Integer.toString(4, 10), "divisibleBy3", "4", true, false, false,
            false),
        new GoldLabelOfString(Integer.toString(5, 10), "divisibleBy3", "5", true, false, false,
            false),
        new GoldLabelOfString(Integer.toString(6, 10), "divisibleBy3", "6", false, true, false,
            false),
        new GoldLabelOfString(Integer.toString(7, 10), "divisibleBy3", "7", true, false, false,
            false),
        new GoldLabelOfString(Integer.toString(8, 10), "divisibleBy3", "8", true, false, false,
            false),
        new GoldLabelOfString(Integer.toString(9, 10), "divisibleBy3", "9", false, false, true,
            false),
        new GoldLabelOfString(Integer.toString(10, 10), "divisibleBy3", "10", false, false, false,
            true),
        new GoldLabelOfString(Integer.toString(11, 10), "divisibleBy3", "11", false, false, false,
            true),
        new GoldLabelOfString(Integer.toString(12, 10), "divisibleBy3", "12", false, false, true,
            false));
  }

  private List<Map.Entry<String, FeatureVector<Integer>>> isDivisibleBy2Correct() {
    return Lists.newArrayList(
        new AbstractMap.SimpleEntry<>("1", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("5", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("6", FeatureVector.of(new int[] {OK, OK, OK})),
        new AbstractMap.SimpleEntry<>("7", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("11", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("12", FeatureVector.of(new int[] {OK, OK, OK})));
  }

  private List<Map.Entry<String, FeatureVector<Integer>>> isDivisibleBy2Incorrect() {
    return Lists.newArrayList(
        new AbstractMap.SimpleEntry<>("2", FeatureVector.of(new int[] {OK, KO, KO})),
        new AbstractMap.SimpleEntry<>("3", FeatureVector.of(new int[] {KO, OK, KO})),
        new AbstractMap.SimpleEntry<>("4", FeatureVector.of(new int[] {OK, KO, KO})),
        new AbstractMap.SimpleEntry<>("8", FeatureVector.of(new int[] {OK, KO, KO})),
        new AbstractMap.SimpleEntry<>("9", FeatureVector.of(new int[] {KO, OK, KO})),
        new AbstractMap.SimpleEntry<>("10", FeatureVector.of(new int[] {OK, KO, KO})));
  }

  private List<Map.Entry<String, FeatureVector<Integer>>> isDivisibleBy3Correct() {
    return Lists.newArrayList(
        new AbstractMap.SimpleEntry<>("1", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("2", FeatureVector.of(new int[] {OK, KO, KO})),
        new AbstractMap.SimpleEntry<>("3", FeatureVector.of(new int[] {KO, OK, KO})),
        new AbstractMap.SimpleEntry<>("4", FeatureVector.of(new int[] {OK, KO, KO})),
        new AbstractMap.SimpleEntry<>("5", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("6", FeatureVector.of(new int[] {OK, OK, OK})),
        new AbstractMap.SimpleEntry<>("7", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("8", FeatureVector.of(new int[] {OK, KO, KO})),
        new AbstractMap.SimpleEntry<>("9", FeatureVector.of(new int[] {KO, OK, KO})),
        new AbstractMap.SimpleEntry<>("10", FeatureVector.of(new int[] {OK, KO, KO})),
        new AbstractMap.SimpleEntry<>("11", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("12", FeatureVector.of(new int[] {OK, OK, OK})));
  }

  private List<Map.Entry<String, FeatureVector<Integer>>> isDivisibleBy3Incorrect() {
    return null;
  }

  private List<Map.Entry<String, FeatureVector<Integer>>> isDivisibleBy6Correct() {
    return Lists.newArrayList(
        new AbstractMap.SimpleEntry<>("1", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("2", FeatureVector.of(new int[] {OK, KO, KO})),
        new AbstractMap.SimpleEntry<>("4", FeatureVector.of(new int[] {OK, KO, KO})),
        new AbstractMap.SimpleEntry<>("5", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("6", FeatureVector.of(new int[] {OK, OK, OK})),
        new AbstractMap.SimpleEntry<>("7", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("8", FeatureVector.of(new int[] {OK, KO, KO})),
        new AbstractMap.SimpleEntry<>("10", FeatureVector.of(new int[] {OK, KO, KO})),
        new AbstractMap.SimpleEntry<>("11", FeatureVector.of(new int[] {KO, KO, KO})),
        new AbstractMap.SimpleEntry<>("12", FeatureVector.of(new int[] {OK, OK, OK})));
  }

  private List<Map.Entry<String, FeatureVector<Integer>>> isDivisibleBy6Incorrect() {
    return Lists.newArrayList(
        new AbstractMap.SimpleEntry<>("3", FeatureVector.of(new int[] {KO, OK, KO})),
        new AbstractMap.SimpleEntry<>("9", FeatureVector.of(new int[] {KO, OK, KO})));
  }

  private List<Summary> summaries() {
    return Lists.newArrayList(
        new Summary("isDivisibleBy2", Sets.newHashSet("OK", "KO"), 1.0, 0.6666666666666666, 0.5, 6,
            6, 0, Sets.newHashSet("isDivisibleBy3", "isDivisibleBy6"),
            Sets.newHashSet("isDivisibleBy3", "isDivisibleBy6")),
        new Summary("isDivisibleBy3", Sets.newHashSet("OK", "KO"), 1.0, 0.8333333333333334, 0.5, 12,
            0, 0, Sets.newHashSet("isDivisibleBy2", "isDivisibleBy6"),
            Sets.newHashSet("isDivisibleBy2", "isDivisibleBy6")),
        new Summary("isDivisibleBy6", Sets.newHashSet("OK", "KO"), 1.0, 1.0, 0.5, 10, 2, 0,
            Sets.newHashSet("isDivisibleBy2", "isDivisibleBy3"),
            Sets.newHashSet("isDivisibleBy2", "isDivisibleBy3")));
  }
}
