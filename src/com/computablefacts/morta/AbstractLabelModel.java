package com.computablefacts.morta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;

import smile.stat.hypothesis.CorTest;

@CheckReturnValue
public abstract class AbstractLabelModel<T> {

  private final Dictionary lfNames_;
  private final Dictionary lfLabels_;
  private final List<ILabelingFunction<T>> lfs_;
  private final List<IGoldLabel<T>> goldLabels;

  /**
   * Constructor.
   *
   * @param lfNames lfNames mapping of the labeling function names to integers. Each integer
   *        represents the position of the labeling function in the lfs list.
   * @param lfLabels mapping of the labeling function outputs, i.e. labels, to integers. Each
   *        integer represents a machine-friendly version of a human-readable label.
   * @param lfs labeling functions.
   * @param goldLabels gold labels.
   */
  public AbstractLabelModel(Dictionary lfNames, Dictionary lfLabels, List<ILabelingFunction<T>> lfs,
      List<IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(lfNames, "lfNames should not be null");
    Preconditions.checkNotNull(lfLabels, "lfLabels should not be null");
    Preconditions.checkNotNull(lfs, "lfs should not be null");
    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(lfLabels.size() >= 2, "cardinality must be >= 2");

    lfNames_ = lfNames;
    lfLabels_ = lfLabels;
    lfs_ = new ArrayList<>(lfs);
    this.goldLabels = new ArrayList<>(goldLabels);
  }

  /**
   * Compute correlation between each pair of labeling functions.
   * 
   * @param correlation correlation type.
   * @return a correlation matrix.
   */
  public Table<String, String, CorTest> labelingFunctionsCorrelations(
      Summary.eCorrelation correlation) {
    return Summary.labelingFunctionsCorrelations(lfNames_, lfLabels_,
        Pipeline.on(goldLabels).transform(IGoldLabel::data).label(lfs_).collect(), correlation);
  }

  /**
   * Explore the labeling functions outputs.
   *
   * @return a segmentation of the data according to the output produced by each labeling function.
   */
  public Table<String, Summary.eStatus, List<Map.Entry<T, FeatureVector<Integer>>>> explore() {
    return Summary.explore(lfNames_, lfLabels_,
        Pipeline.on(goldLabels).transform(IGoldLabel::data).label(lfs_).collect(),
        Pipeline.on(goldLabels).transform(gl -> lfLabels_.id(gl.label())).collect());
  }

  /**
   * Compute a {@link Summary} object with polarity, coverage, overlaps, etc. for each labeling
   * function. When gold labels are provided, this method will compute the number of correct and
   * incorrect labels output by each labeling function.
   * 
   * @return a {@link Summary} object for each labeling function.
   */
  public List<Summary> summary() {
    return Summary.summarize(lfNames_, lfLabels_,
        Pipeline.on(goldLabels).transform(IGoldLabel::data).label(lfs_).collect(),
        Pipeline.on(goldLabels).transform(gl -> lfLabels_.id(gl.label())).collect());
  }

  protected Dictionary lfNames() {
    return lfNames_;
  }

  protected Dictionary lfLabels() {
    return lfLabels_;
  }

  protected List<ILabelingFunction<T>> lfs() {
    return lfs_;
  }

  protected List<IGoldLabel<T>> goldLabels() {
    return goldLabels;
  }

  public abstract void fit();

  public abstract List<Integer> predict();
}
