package com.computablefacts.morta.classifiers;

import static com.computablefacts.morta.snorkel.ILabelingFunction.KO;
import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.View;
import com.computablefacts.morta.labelmodels.MajorityLabelModel;
import com.computablefacts.morta.snorkel.*;
import com.google.common.collect.Lists;

public class LogisticRegressionClassifierTest {

  @Test
  public void testTrain() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);
    lfNames.put("isDivisibleBy6", 2);

    // OK = isDivisibleBy2 AND isDivisibleBy3
    // KO = !isDivisibleBy2 OR !isDivisibleBy3
    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", OK);
    lfLabels.put("KO", KO);

    List<ILabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? OK : KO);
    lfs.add(x -> x % 3 == 0 ? OK : KO);
    lfs.add(x -> x % 6 == 0 ? OK : KO);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    ITransformationFunction<Integer, FeatureVector<Double>> transform = x -> {

      String number = new StringBuilder(Integer.toBinaryString(x)).reverse().toString();
      FeatureVector<Double> vector = new FeatureVector<>(8, 0.0);

      for (int i = 0; i < number.length(); i++) {
        vector.set(i, Double.parseDouble(Character.toString(number.charAt(i))));
      }
      return vector;
    };

    List<FeatureVector<Double>> insts = View.of(instances).map(transform).toList();

    List<FeatureVector<Double>> probs = MajorityLabelModel.probabilities(lfNames, lfLabels,
        View.of(instances).map(Helpers.label(lfs)).map(Map.Entry::getValue).toList());

    List<Integer> preds = MajorityLabelModel.predictions(lfNames, lfLabels, probs,
        MajorityLabelModel.eTieBreakPolicy.RANDOM, 0.00001);

    LogisticRegressionClassifier classifier = new LogisticRegressionClassifier();
    classifier.train(insts, preds);

    // Here, instances = [1, 2, 3, 4, 5, 6] and goldLabels = ["KO", "KO", "KO", "KO", "KO", "OK"]
    List<Integer> goldLabels = Lists.newArrayList(KO, KO, KO, KO, KO, OK);

    List<Integer> predictions = instances.stream().map(i -> classifier.predict(transform.apply(i)))
        .collect(Collectors.toList());

    ConfusionMatrix matrix = new ConfusionMatrix();
    matrix.addAll(goldLabels, predictions, 1, 0);

    Assert.assertEquals(1, matrix.nbTruePositives());
    Assert.assertEquals(5, matrix.nbTrueNegatives());
    Assert.assertEquals(0, matrix.nbFalsePositives());
    Assert.assertEquals(0, matrix.nbFalseNegatives());
  }

  @Test
  public void testPredict() {
    // TODO
  }
}
