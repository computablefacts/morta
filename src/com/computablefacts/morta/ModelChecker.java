package com.computablefacts.morta;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

@CheckReturnValue
final public class ModelChecker {

  private ModelChecker() {}

  // result[0] = number of correct results
  // result[1] = number of incorrect results
  public static List<Integer> accuracy(List<Integer> predictions, List<Integer> goldLabels) {

    Preconditions.checkNotNull(predictions, "predictions should not be null");
    Preconditions.checkNotNull(goldLabels, "lfNames should not be null");
    Preconditions.checkArgument(predictions.size() == goldLabels.size(),
        "Mismatch between the number of predictions and the number of gold labels : %s vs %s",
        predictions.size(), goldLabels.size());

    @Var
    int nbCorrect = 0;
    @Var
    int nbIncorrect = 0;

    for (int i = 0; i < predictions.size(); i++) {

      int actual = goldLabels.get(i);
      int predicted = predictions.get(i);

      if (actual == predicted) {
        nbCorrect++;
      } else {
        nbIncorrect++;
      }
    }
    return Lists.newArrayList(nbCorrect, nbIncorrect);
  }
}
