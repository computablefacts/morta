package com.computablefacts.morta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.computablefacts.asterix.ConfusionMatrix;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class GoldLabelOfStringTest {

  @Test
  public void testEqualsAndHashcode() {
    EqualsVerifier.forClass(GoldLabelOfString.class).verify();
  }

  @Test
  public void testFillFromJsonObject() {

    Map<String, Object> json = new HashMap<>();
    json.put("id", "1");
    json.put("label", "json");
    json.put("data", "test");
    json.put("is_true_positive", true);
    json.put("is_false_positive", false);
    json.put("is_true_negative", false);
    json.put("is_false_negative", false);

    GoldLabelOfString goldLabel = new GoldLabelOfString(json);

    Assert.assertEquals("1", goldLabel.id());
    Assert.assertEquals("json", goldLabel.label());
    Assert.assertEquals("test", goldLabel.data());
    Assert.assertEquals(true, goldLabel.isTruePositive());
    Assert.assertEquals(false, goldLabel.isFalsePositive());
    Assert.assertEquals(false, goldLabel.isTrueNegative());
    Assert.assertEquals(false, goldLabel.isFalseNegative());
  }

  @Test
  public void testSplit_25_50_25() {

    List<Set<GoldLabelOfString>> goldLabels = IGoldLabel.split(goldLabels());

    Assert.assertEquals(3, goldLabels.size());
    Assert.assertEquals(2, goldLabels.get(0).size());
    Assert.assertEquals(4, goldLabels.get(1).size());
    Assert.assertEquals(2, goldLabels.get(2).size());
  }

  @Test
  public void testSplit_0_75_25() {

    List<Set<GoldLabelOfString>> goldLabels = IGoldLabel.split(goldLabels(), false, 0, 0.75);

    Assert.assertEquals(3, goldLabels.size());
    Assert.assertEquals(0, goldLabels.get(0).size());
    Assert.assertEquals(6, goldLabels.get(1).size());
    Assert.assertEquals(2, goldLabels.get(2).size());
  }

  @Test
  public void testProportionalSplit_0_75_25() {

    List<Set<GoldLabelOfString>> goldLabels = IGoldLabel.split(goldLabels(), true, 0, 0.75);

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

  private Set<GoldLabelOfString> goldLabels() {
    return Sets.newHashSet(
        new GoldLabelOfString(Integer.toString(1, 10), "test1", "test1", false, true, false, false),
        new GoldLabelOfString(Integer.toString(2, 10), "test1", "test2", false, true, false, false),
        new GoldLabelOfString(Integer.toString(3, 10), "test1", "test3", false, true, false, false),
        new GoldLabelOfString(Integer.toString(4, 10), "test1", "test4", false, true, false, false),
        new GoldLabelOfString(Integer.toString(5, 10), "test2", "test1", false, true, false, false),
        new GoldLabelOfString(Integer.toString(6, 10), "test2", "test2", false, true, false, false),
        new GoldLabelOfString(Integer.toString(7, 10), "test2", "test3", false, true, false, false),
        new GoldLabelOfString(Integer.toString(8, 10), "test2", "test4", false, true, false,
            false));
  }
}
