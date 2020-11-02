package com.computablefacts.morta.snorkel;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

public class MedianLabelModelTest {

  @Test
  public void testPredict() {

    List<Integer> list = labelModel().predict();

    Assert.assertEquals(Lists.newArrayList(0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1), list);
  }

  private MedianLabelModel<String> labelModel() {
    return new MedianLabelModel<>(lfs(), goldLabels());
  }

  private List<AbstractLabelingFunction<String>> lfs() {

    List<AbstractLabelingFunction<String>> lfs = new ArrayList<>();
    lfs.add(new AbstractLabelingFunction<String>("isDivisibleBy2") {

      @Override
      public Integer apply(String s) {
        return Integer.parseInt(s, 10) % 2 == 0 ? MedianLabelModel.LABEL_OK
            : MedianLabelModel.LABEL_KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<String>("isDivisibleBy3") {

      @Override
      public Integer apply(String s) {
        return Integer.parseInt(s, 10) % 3 == 0 ? MedianLabelModel.LABEL_OK
            : MedianLabelModel.LABEL_KO;
      }
    });
    lfs.add(new AbstractLabelingFunction<String>("isDivisibleBy6") {

      @Override
      public Integer apply(String s) {
        return Integer.parseInt(s, 10) % 6 == 0 ? MedianLabelModel.LABEL_OK
            : MedianLabelModel.LABEL_KO;
      }
    });

    return lfs;
  }

  private List<IGoldLabel<String>> goldLabels() {

    // OK = isDivisibleBy3
    // KO = !isDivisibleBy3
    return Lists.newArrayList(
        new GoldLabel(Integer.toString(1, 10), "divisibleBy3", "1", false, true, false, false),
        new GoldLabel(Integer.toString(2, 10), "divisibleBy3", "2", false, true, false, false),
        new GoldLabel(Integer.toString(3, 10), "divisibleBy3", "3", true, false, false, false),
        new GoldLabel(Integer.toString(4, 10), "divisibleBy3", "4", false, false, true, false),
        new GoldLabel(Integer.toString(5, 10), "divisibleBy3", "5", false, false, true, false),
        new GoldLabel(Integer.toString(6, 10), "divisibleBy3", "6", true, false, false, false),
        new GoldLabel(Integer.toString(7, 10), "divisibleBy3", "7", false, false, true, false),
        new GoldLabel(Integer.toString(8, 10), "divisibleBy3", "8", false, false, true, false),
        new GoldLabel(Integer.toString(9, 10), "divisibleBy3", "9", false, false, false, true),
        new GoldLabel(Integer.toString(10, 10), "divisibleBy3", "10", false, true, false, false),
        new GoldLabel(Integer.toString(11, 10), "divisibleBy3", "11", false, true, false, false),
        new GoldLabel(Integer.toString(12, 10), "divisibleBy3", "12", false, false, false, true));
  }
}
