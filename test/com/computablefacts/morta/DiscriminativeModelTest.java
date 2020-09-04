package com.computablefacts.morta;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import smile.classification.LogisticRegression;

public class DiscriminativeModelTest {

  @Test
  public void testTrainLogisticRegression() {

    Dictionary lfNames = new Dictionary();
    lfNames.put("isDivisibleBy2", 0);
    lfNames.put("isDivisibleBy3", 1);
    lfNames.put("isDivisibleBy6", 2);

    // OK = isDivisibleBy2 AND isDivisibleBy3
    // KO = !isDivisibleBy2 OR !isDivisibleBy3
    Dictionary lfLabels = new Dictionary();
    lfLabels.put("OK", 1);
    lfLabels.put("KO", 0);

    List<LabelingFunction<Integer>> lfs = new ArrayList<>();
    lfs.add(x -> x % 2 == 0 ? 1 : 0);
    lfs.add(x -> x % 3 == 0 ? 1 : 0);
    lfs.add(x -> x % 6 == 0 ? 1 : 0);

    List<Integer> instances = Lists.newArrayList(1, 2, 3, 4, 5, 6);

    TransformationFunction<Integer, FeatureVector<Double>> transform = x -> {

      String number = new StringBuilder(Integer.toBinaryString(x)).reverse().toString();
      FeatureVector<Double> vector = new FeatureVector<>(8, 0.0);

      for (int i = 0; i < number.length(); i++) {
        vector.set(i, Double.parseDouble(Character.toString(number.charAt(i))));
      }
      return vector;
    };

    LogisticRegression logisticRegression =
        DiscriminativeModel.trainLogisticRegression(lfNames, lfLabels, lfs, instances, transform);

    // Here, instances = [1, 2, 3, 4, 5, 6] and goldLabels = ["KO", "KO", "KO", "KO", "KO", "OK"]
    List<Integer> goldLabels = Lists.newArrayList(0, 0, 0, 0, 0, 1);

    List<Integer> predictions =
        instances.stream().map(i -> logisticRegression.predict(transform.apply(i).toDoubleArray()))
            .collect(Collectors.toList());
    List<Integer> accuracy = ModelChecker.accuracy(predictions, goldLabels);

    Assert.assertEquals(2, accuracy.size());
    Assert.assertEquals((Integer) 6, accuracy.get(0));
    Assert.assertEquals((Integer) 0, accuracy.get(1));
  }

  @Test
  public void testLogisticRegression() {
    // TODO
  }
}
