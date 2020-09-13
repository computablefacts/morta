package com.computablefacts.morta;

import static com.computablefacts.morta.ILabelingFunction.ABSTAIN;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

@CheckReturnValue
final public class Summarizer {

  private Summarizer() {}

  static List<Summary> summaries(Dictionary lfNames, Dictionary lfLabels,
      List<FeatureVector<Integer>> instances, List<Integer> goldLabels) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(lfLabels, "lfLabels should not be null");
    Preconditions.checkNotNull(instances, "instances should not be null");

    int nbLabelingFunctions = lfNames.size();
    List<Summary> summaries = new ArrayList<>(nbLabelingFunctions);

    Preconditions.checkState(goldLabels == null || instances.size() == goldLabels.size(),
        "Mismatch between the number of instances and the number of gold labels : %s vs %s",
        instances.size(), goldLabels == null ? 0 : goldLabels.size());

    for (int i = 0; i < nbLabelingFunctions; i++) {

      String labelingFunctionName = lfNames.label(i);
      Set<String> labels = new HashSet<>();
      @Var
      double nbLabelled = 0;
      @Var
      double nbOverlaps = 0;
      @Var
      double nbConflicts = 0;
      @Var
      double nbDataPoints = 0;
      @Var
      int nbCorrect = goldLabels == null ? -1 : 0;
      @Var
      int nbIncorrect = goldLabels == null ? -1 : 0;

      for (int j = 0; j < instances.size(); j++) {

        nbDataPoints += 1.0;
        FeatureVector<Integer> featureVector = instances.get(j);
        String lfName = lfNames.label(i);
        int lfValue = featureVector.get(i);

        Preconditions.checkState(nbLabelingFunctions == featureVector.size(),
            "Invalid feature vector length : %s found vs %s expected", featureVector.size(),
            nbLabelingFunctions);
        Preconditions.checkState(labelingFunctionName.equals(lfName),
            "Invalid labeling function name : %s found vs %s expected", lfName,
            labelingFunctionName);

        if (lfValue > ABSTAIN) {
          if (goldLabels != null) {
            if (lfValue == goldLabels.get(j)) {
              nbCorrect++;
            } else {
              nbIncorrect++;
            }
          }

          nbLabelled += 1.0;
          labels.add(lfLabels.label(lfValue));

          @Var
          boolean hasOverlap = false;
          @Var
          boolean hasConflict = false;

          for (int k = 0; (!hasOverlap || !hasConflict) && k < nbLabelingFunctions; k++) {
            if (k != i) {

              int lfv = featureVector.get(k);

              if (!hasOverlap && lfv > ABSTAIN && lfv == lfValue) {
                nbOverlaps += 1.0;
                hasOverlap = true;
              }
              if (!hasConflict && lfv > ABSTAIN && lfv != lfValue) {
                nbConflicts += 1.0;
                hasConflict = true;
              }
            }
          }
        }
      }

      Preconditions.checkState(goldLabels == null || nbCorrect + nbIncorrect == instances.size(),
          "Mismatch between the number of correct/incorrect labels and the number of instances : %s found vs %s expected",
          nbCorrect + nbIncorrect, instances.size());

      summaries.add(new Summary(labelingFunctionName, labels, nbLabelled / nbDataPoints,
          nbOverlaps / nbLabelled, nbConflicts / nbLabelled, nbCorrect, nbIncorrect));
    }
    return summaries;
  }
}
