package com.computablefacts.morta;

import static com.computablefacts.morta.Explorer.eCorrelation.KENDALL;
import static com.computablefacts.morta.Explorer.eCorrelation.SPEARMAN;
import static com.computablefacts.morta.ILabelingFunction.ABSTAIN;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import smile.stat.hypothesis.CorTest;

final public class Explorer {

  private Explorer() {}

  public static Table<String, String, CorTest> labelingFunctionsCorrelations(Dictionary lfNames,
      List<FeatureVector<Integer>> instances, eCorrelation correlation) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(instances, "instances should not be null");
    Preconditions.checkNotNull(correlation, "correlation should not be null");

    int nbLabelingFunctions = lfNames.size();
    List<double[]> matrix = new ArrayList<>(nbLabelingFunctions);

    // Transpose
    for (int i = 0; i < nbLabelingFunctions; i++) {

      double[] vector = new double[instances.size()];

      for (int j = 0; j < instances.size(); j++) {
        vector[j] = instances.get(j).get(i);
      }

      matrix.add(vector);
    }

    // Compute correlation coefficient between each LF
    Table<String, String, CorTest> correlations = HashBasedTable.create();

    for (int i = 0; i < matrix.size(); i++) {
      for (int j = 0; j < matrix.size(); j++) {

        double[] lf1 = matrix.get(i);
        double[] lf2 = matrix.get(j);

        if (KENDALL.equals(correlation)) {
          correlations.put(lfNames.label(i), lfNames.label(j), CorTest.kendall(lf1, lf2));
        } else if (SPEARMAN.equals(correlation)) {
          correlations.put(lfNames.label(i), lfNames.label(j), CorTest.spearman(lf1, lf2));
        } else { // PEARSON
          correlations.put(lfNames.label(i), lfNames.label(j), CorTest.pearson(lf1, lf2));
        }
      }
    }
    return correlations;
  }

  public static <D> Table<String, eStatus, List<Map.Entry<D, FeatureVector<Integer>>>> explore(
      Dictionary lfNames, List<Map.Entry<D, FeatureVector<Integer>>> instances,
      List<Integer> goldLabels) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(instances, "instances should not be null");
    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(instances.size() == goldLabels.size(),
        "Mismatch between the number of instances and the number of gold labels : %s vs %s",
        instances.size(), goldLabels.size());

    int nbLabelingFunctions = lfNames.size();
    Table<String, eStatus, List<Map.Entry<D, FeatureVector<Integer>>>> table =
        HashBasedTable.create();

    for (int i = 0; i < nbLabelingFunctions; i++) {

      String lfName = lfNames.label(i);

      for (int j = 0; j < instances.size(); j++) {

        Map.Entry<D, FeatureVector<Integer>> instance = instances.get(j);
        int lfLabel = instance.getValue().get(i);

        Preconditions.checkState(nbLabelingFunctions == instance.getValue().size(),
            "Invalid feature vector length : %s found vs %s expected", instance.getValue().size(),
            nbLabelingFunctions);

        if (lfLabel == goldLabels.get(j)) {
          if (lfLabel == ABSTAIN) {
            if (!table.contains(lfName, eStatus.CORRECT_ABSTAIN)) {
              table.put(lfName, eStatus.CORRECT_ABSTAIN, new ArrayList<>());
            }
            table.get(lfName, eStatus.CORRECT_ABSTAIN).add(instance);
          } else {
            if (!table.contains(lfName, eStatus.CORRECT)) {
              table.put(lfName, eStatus.CORRECT, new ArrayList<>());
            }
            table.get(lfName, eStatus.CORRECT).add(instance);
          }
        } else {
          if (lfLabel == ABSTAIN) {
            if (!table.contains(lfName, eStatus.INCORRECT_ABSTAIN)) {
              table.put(lfName, eStatus.INCORRECT_ABSTAIN, new ArrayList<>());
            }
            table.get(lfName, eStatus.INCORRECT_ABSTAIN).add(instance);
          } else {
            if (!table.contains(lfName, eStatus.INCORRECT)) {
              table.put(lfName, eStatus.INCORRECT, new ArrayList<>());
            }
            table.get(lfName, eStatus.INCORRECT).add(instance);
          }
        }
      }
    }
    return table;
  }

  /**
   * Note that Spearman is computed on ranks and so depicts monotonic relationships while Pearson is
   * on true values and depicts linear relationships. If Spearman > Pearson the correlation is
   * monotonic but not linear.
   */
  public enum eCorrelation {
    PEARSON, KENDALL, SPEARMAN
  }

  public enum eStatus {
    ALL, CORRECT, /* the LF output the same label as the gold one */
    INCORRECT, /* the LF output a label different from the gold one */
    CORRECT_ABSTAIN, /* both the LF and the gold label are ABSTAIN */
    INCORRECT_ABSTAIN /* the LF output is ABSTAIN but the gold one is not */
  }
}
