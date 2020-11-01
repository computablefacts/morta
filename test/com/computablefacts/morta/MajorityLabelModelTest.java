package com.computablefacts.morta;

import static com.computablefacts.morta.ILabelingFunction.ABSTAIN;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.google.common.collect.Lists;

public class MajorityLabelModelTest {

  @Test
  public void testProbabilities1() {

    List<FeatureVector<Double>> goldProbs = Lists.newArrayList(
        FeatureVector.from(new double[] {1.0, 0.0}), FeatureVector.from(new double[] {0.5, 0.5}),
        FeatureVector.from(new double[] {0.5, 0.5}));

    Dictionary lfNames = new Dictionary();
    lfNames.put("lf1", 0);
    lfNames.put("lf2", 1);
    lfNames.put("lf3", 2);

    Dictionary lfLabels = new Dictionary();
    lfLabels.put("KO", 0);
    lfLabels.put("OK", 1);

    List<FeatureVector<Integer>> instances =
        Lists.newArrayList(FeatureVector.from(new int[] {0, 0, ABSTAIN}),
            FeatureVector.from(new int[] {ABSTAIN, 0, 1}),
            FeatureVector.from(new int[] {1, ABSTAIN, 0}));

    List<FeatureVector<Double>> probabilities =
        MajorityLabelModel.probabilities(lfNames, lfLabels, instances);

    Assert.assertEquals(instances.size(), probabilities.size());
    Assert.assertEquals(goldProbs, probabilities);
  }

  @Test
  public void testProbabilities2() {

    List<FeatureVector<Double>> goldProbs = Lists.newArrayList(
        FeatureVector.from(new double[] {0.0, 1.0}), FeatureVector.from(new double[] {1.0, 0.0}),
        FeatureVector.from(new double[] {0.0, 1.0}), FeatureVector.from(new double[] {1.0, 0.0}),
        FeatureVector.from(new double[] {1.0, 0.0}), FeatureVector.from(new double[] {1.0, 0.0}),
        FeatureVector.from(new double[] {0.5, 0.5}), FeatureVector.from(new double[] {1.0, 0.0}),
        FeatureVector.from(new double[] {0.0, 1.0}), FeatureVector.from(new double[] {0.0, 1.0}),
        FeatureVector.from(new double[] {0.0, 1.0}), FeatureVector.from(new double[] {1.0, 0.0}),
        FeatureVector.from(new double[] {0.0, 1.0}), FeatureVector.from(new double[] {1.0, 0.0}),
        FeatureVector.from(new double[] {0.5, 0.5}));

    Dictionary lfNames = new Dictionary();
    lfNames.put("lf1", 0);
    lfNames.put("lf2", 1);
    lfNames.put("lf3", 2);
    lfNames.put("lf4", 3);
    lfNames.put("lf5", 4);
    lfNames.put("lf6", 5);
    lfNames.put("lf7", 6);
    lfNames.put("lf8", 7);
    lfNames.put("lf9", 8);
    lfNames.put("lf10", 9);

    Dictionary lfLabels = new Dictionary();
    lfLabels.put("KO", 0);
    lfLabels.put("OK", 1);

    List<FeatureVector<Integer>> instances =
        Lists.newArrayList(FeatureVector.from(new int[] {-1, -1, -1, -1, -1, -1, 1, -1, -1, -1}),
            FeatureVector.from(new int[] {0, -1, -1, -1, -1, -1, -1, -1, -1, 0}),
            FeatureVector.from(new int[] {-1, 1, 1, -1, -1, -1, -1, -1, -1, -1}),
            FeatureVector.from(new int[] {-1, -1, -1, -1, -1, 0, -1, -1, -1, -1}),
            FeatureVector.from(new int[] {-1, -1, -1, -1, -1, 0, -1, -1, -1, 0}),
            FeatureVector.from(new int[] {0, -1, -1, -1, -1, -1, -1, -1, -1, -1}),
            FeatureVector.from(new int[] {-1, -1, -1, -1, -1, 0, 1, -1, -1, -1}),
            FeatureVector.from(new int[] {-1, -1, -1, -1, -1, 0, -1, -1, -1, -1}),
            FeatureVector.from(new int[] {-1, 1, -1, -1, 1, 0, 1, -1, -1, 0}),
            FeatureVector.from(new int[] {-1, 1, 1, -1, -1, -1, -1, -1, -1, 0}),
            FeatureVector.from(new int[] {0, 1, 1, -1, -1, -1, -1, -1, -1, -1}),
            FeatureVector.from(new int[] {0, -1, -1, -1, -1, -1, -1, -1, 0, 0}),
            FeatureVector.from(new int[] {-1, 1, 1, -1, 1, -1, -1, -1, -1, 0}),
            FeatureVector.from(new int[] {0, -1, -1, -1, -1, -1, -1, -1, -1, 0}),
            FeatureVector.from(new int[] {-1, -1, 1, -1, -1, -1, -1, -1, -1, 0}));

    List<FeatureVector<Double>> probabilities =
        MajorityLabelModel.probabilities(lfNames, lfLabels, instances);

    Assert.assertEquals(instances.size(), probabilities.size());
    Assert.assertEquals(goldProbs, probabilities);
  }

  @Test
  public void testProbability() {

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

    List<FeatureVector<Double>> probabilities = MajorityLabelModel.probabilities(lfNames, lfLabels,
        Pipeline.on(instances).label(lfs).transform(Map.Entry::getValue).collect());

    Assert.assertEquals(instances.size(), probabilities.size());
    Assert.assertEquals(goldProbs, probabilities);
  }

  @Test
  public void testPrediction() {

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

    List<FeatureVector<Double>> probabilities = MajorityLabelModel.probabilities(lfNames, lfLabels,
        Pipeline.on(instances).label(lfs).transform(Map.Entry::getValue).collect());

    List<Integer> predictions = MajorityLabelModel.predictions(lfNames, lfLabels, probabilities,
        MajorityLabelModel.eTieBreakPolicy.RANDOM, 0.00001);

    Assert.assertEquals(instances.size(), predictions.size());
    Assert.assertEquals(goldLabels, predictions);
  }

  @Test
  public void testConfusionMatrix() {

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

    List<FeatureVector<Double>> probabilities = MajorityLabelModel.probabilities(lfNames, lfLabels,
        Pipeline.on(instances).label(lfs).transform(Map.Entry::getValue).collect());

    List<Integer> predictions = MajorityLabelModel.predictions(lfNames, lfLabels, probabilities,
        MajorityLabelModel.eTieBreakPolicy.RANDOM, 0.00001);

    ConfusionMatrix matrix = new ConfusionMatrix();
    matrix.addAll(goldLabels, predictions, 1, 0);

    Assert.assertEquals(1, matrix.nbTruePositives());
    Assert.assertEquals(5, matrix.nbTrueNegatives());
    Assert.assertEquals(0, matrix.nbFalsePositives());
    Assert.assertEquals(0, matrix.nbFalseNegatives());
  }
}
