package com.computablefacts.morta;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.google.common.collect.Sets;

public class GoldLabelTest {

  @Test
  public void testSplit_25_50_25() {

    List<Set<GoldLabel>> goldLabels = IGoldLabel.split(goldLabels());

    Assert.assertEquals(3, goldLabels.size());
    Assert.assertEquals(2, goldLabels.get(0).size());
    Assert.assertEquals(4, goldLabels.get(1).size());
    Assert.assertEquals(2, goldLabels.get(2).size());
  }

  @Test
  public void testSplit_0_75_25() {

    List<Set<GoldLabel>> goldLabels = IGoldLabel.split(goldLabels(), false, 0, 0.75);

    Assert.assertEquals(3, goldLabels.size());
    Assert.assertEquals(0, goldLabels.get(0).size());
    Assert.assertEquals(6, goldLabels.get(1).size());
    Assert.assertEquals(2, goldLabels.get(2).size());
  }

  @Test
  public void testProportionalSplit_0_75_25() {

    List<Set<GoldLabel>> goldLabels = IGoldLabel.split(goldLabels(), true, 0, 0.75);

    Assert.assertEquals(3, goldLabels.size());
    Assert.assertEquals(0, goldLabels.get(0).size());
    Assert.assertEquals(6, goldLabels.get(1).size());
    Assert.assertEquals(2, goldLabels.get(2).size());
  }

  @Test
  public void testConfusionMatrix() {

    ConfusionMatrix confusionMatrix = IGoldLabel.confusionMatrix(goldLabels().stream()
        .filter(gl -> gl.label().equals("test1")).collect(Collectors.toList()));

    Assert.assertEquals(4, confusionMatrix.nbTruePositives());
    Assert.assertEquals(0, confusionMatrix.nbTrueNegatives());
    Assert.assertEquals(0, confusionMatrix.nbFalsePositives());
    Assert.assertEquals(0, confusionMatrix.nbFalseNegatives());
  }

  private Set<GoldLabel> goldLabels() {
    return Sets.newHashSet(new GoldLabel(1, "test1", "test1", true, false, false, false),
        new GoldLabel(1, "test1", "test2", true, false, false, false),
        new GoldLabel(1, "test1", "test3", true, false, false, false),
        new GoldLabel(1, "test1", "test4", true, false, false, false),
        new GoldLabel(1, "test2", "test1", true, false, false, false),
        new GoldLabel(1, "test2", "test2", true, false, false, false),
        new GoldLabel(1, "test2", "test3", true, false, false, false),
        new GoldLabel(1, "test2", "test4", true, false, false, false));
  }

  private static class GoldLabel implements IGoldLabel<String> {

    private final String id_;
    private final String label_;
    private final String data_;
    private final boolean isTruePositive_;
    private final boolean isFalsePositive_;
    private final boolean isTrueNegative_;
    private final boolean isFalseNegative_;

    public GoldLabel(int id, String label, String data, boolean isTruePositive,
        boolean isFalsePositive, boolean isTrueNegative, boolean isFalseNegative) {
      id_ = Integer.toString(id, 10);
      label_ = label;
      data_ = data;
      isTruePositive_ = isTruePositive;
      isFalsePositive_ = isFalsePositive;
      isTrueNegative_ = isTrueNegative;
      isFalseNegative_ = isFalseNegative;
    }

    @Override
    public String id() {
      return id_;
    }

    @Override
    public String label() {
      return label_;
    }

    @Override
    public String data() {
      return data_;
    }

    @Override
    public boolean isTruePositive() {
      return isTruePositive_;
    }

    @Override
    public boolean isFalsePositive() {
      return isFalsePositive_;
    }

    @Override
    public boolean isTrueNegative() {
      return isTrueNegative_;
    }

    @Override
    public boolean isFalseNegative() {
      return isFalseNegative_;
    }
  }
}
