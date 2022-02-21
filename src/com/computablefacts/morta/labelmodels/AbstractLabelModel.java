package com.computablefacts.morta.labelmodels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.computablefacts.asterix.View;
import com.computablefacts.morta.*;
import com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;

import smile.stat.hypothesis.CorTest;

@CheckReturnValue
public abstract class AbstractLabelModel<T> {

  private final Dictionary lfNames_;
  private final Dictionary lfLabels_;
  private final List<? extends AbstractLabelingFunction<T>> lfs_;
  private double mcc_;
  private double f1_;

  /**
   * Constructor.
   *
   * @param lfNames lfNames mapping of the labeling function names to integers. Each integer
   *        represents the position of the labeling function in the lfs list.
   * @param lfLabels mapping of the labeling function outputs, i.e. labels, to integers. Each
   *        integer represents a machine-friendly version of a human-readable label.
   * @param lfs labeling functions.
   */
  public AbstractLabelModel(Dictionary lfNames, Dictionary lfLabels,
      List<? extends AbstractLabelingFunction<T>> lfs) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(lfLabels, "lfLabels should not be null");
    Preconditions.checkNotNull(lfs, "lfs should not be null");
    Preconditions.checkArgument(lfLabels.size() >= 2, "cardinality must be >= 2");

    lfNames_ = lfNames;
    lfLabels_ = lfLabels;
    lfs_ = new ArrayList<>(lfs);
  }

  /**
   * Set the Matthews correlation coefficient associated with this label model.
   *
   * @param mcc Matthews correlation coefficient.
   */
  public void mcc(double mcc) {
    mcc_ = mcc;
  }

  /**
   * Get the Matthews correlation coefficient associated with this label model.
   */
  public double mcc() {
    return mcc_;
  }

  /**
   * Set the F1 score associated with this label model.
   *
   * @param f1 F1 score.
   */
  public void f1(double f1) {
    f1_ = f1;
  }

  /**
   * Get the F1 score associated with this label model.
   */
  public double f1() {
    return f1_;
  }

  /**
   * Compute correlation between each pair of labeling functions.
   *
   * @param goldLabels gold labels.
   * @param correlation correlation type.
   * @return a correlation matrix.
   */
  public Table<String, String, CorTest> labelingFunctionsCorrelations(
      List<IGoldLabel<T>> goldLabels, Summary.eCorrelation correlation) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkNotNull(correlation, "correlation should not be null");

    return Summary.labelingFunctionsCorrelations(lfNames_, lfLabels_,
        View.of(goldLabels).map(IGoldLabel::data).map(Helpers.label(lfs_)).toList(), correlation);
  }

  /**
   * Explore the labeling functions outputs.
   *
   * @param goldLabels gold labels.
   * @return a segmentation of the data according to the output produced by each labeling function.
   */
  public Table<String, Summary.eStatus, List<Map.Entry<T, FeatureVector<Integer>>>> explore(
      List<IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Summary.explore(lfNames_, lfLabels_,
        View.of(goldLabels).map(IGoldLabel::data).map(Helpers.label(lfs_)).toList(),
        View.of(goldLabels).map(gl -> lfLabels_.id(gl.label())).toList());
  }

  /**
   * Compute a {@link Summary} object with polarity, coverage, overlaps, etc. for each labeling
   * function. When gold labels are provided, this method will compute the number of correct and
   * incorrect labels output by each labeling function.
   *
   * @param goldLabels gold labels.
   * @return a {@link Summary} object for each labeling function.
   */
  public List<Summary> summarize(List<IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Summary.summarize(lfNames_, lfLabels_,
        View.of(goldLabels).map(IGoldLabel::data).map(Helpers.label(lfs_)).toList(),
        View.of(goldLabels).map(gl -> lfLabels_.id(gl.label())).toList());
  }

  public Dictionary labelingFunctionNames() {
    return lfNames();
  }

  public Dictionary labelingFunctionLabels() {
    return lfLabels();
  }

  public List<? extends AbstractLabelingFunction<T>> labelingFunctions() {
    return lfs();
  }

  public Dictionary lfNames() {
    return lfNames_;
  }

  public Dictionary lfLabels() {
    return lfLabels_;
  }

  public List<? extends AbstractLabelingFunction<T>> lfs() {
    return lfs_;
  }

  /**
   * Setup internal data structures in order to later be able to make predictions.
   * 
   * @param goldLabels gold labels.
   */
  public abstract void fit(List<IGoldLabel<T>> goldLabels);

  /**
   * Make predictions.
   *
   * @param goldLabels gold labels.
   * @return output a prediction for each gold label.
   */
  public abstract List<Integer> predict(List<IGoldLabel<T>> goldLabels);
}
