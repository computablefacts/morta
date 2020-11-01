package com.computablefacts.morta;

import java.util.ArrayList;
import java.util.List;

public class AbstractLabelModelTest {

  // TODO

  private <T> AbstractLabelModel<T> labelModel(Dictionary lfNames, Dictionary lfLabels,
      List<ILabelingFunction<T>> lfs, List<IGoldLabel<T>> goldLabels) {
    return new AbstractLabelModel<T>(lfNames, lfLabels, lfs, goldLabels) {

      @Override
      public void fit() {

      }

      @Override
      public List<Integer> predict() {
        return new ArrayList<>();
      }
    };
  }
}
