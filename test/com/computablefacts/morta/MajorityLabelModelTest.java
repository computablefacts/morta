package com.computablefacts.morta;

import static com.computablefacts.morta.ILabelingFunction.ABSTAIN;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

public class MajorityLabelModelTest {

  @Test
  public void testProbabilitiesBasic() {

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
  public void testProbabilitiesComplex() {

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
  public void testPredictions() {

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
}
