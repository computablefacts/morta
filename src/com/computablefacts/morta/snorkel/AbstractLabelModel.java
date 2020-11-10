package com.computablefacts.morta.snorkel;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.computablefacts.morta.Pipeline;
import com.computablefacts.morta.poc.Helpers;
import com.computablefacts.nona.helpers.Files;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;
import com.thoughtworks.xstream.XStream;

import smile.stat.hypothesis.CorTest;

@CheckReturnValue
public abstract class AbstractLabelModel<T> {

  private final Dictionary lfNames_;
  private final Dictionary lfLabels_;
  private final List<? extends AbstractLabelingFunction<T>> lfs_;

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

  public static <U extends AbstractLabelModel<?>> void serialize(String path, String filename,
      U u) {

    Preconditions.checkNotNull(path, "path should not be null");
    Preconditions.checkNotNull(filename, "filename should not be null");

    XStream xStream = Helpers.xStream();

    @Var
    File input = new File(path + "\\label_model_" + filename + ".xml");
    @Var
    File output = new File(path + "\\label_model_" + filename + ".xml.gz");

    com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(u));
    com.computablefacts.nona.helpers.Files.gzip(input, output);
    com.computablefacts.nona.helpers.Files.delete(input);
  }

  public static <U extends AbstractLabelModel<?>> U deserialize(String path, String filename) {

    Preconditions.checkNotNull(path, "path should not be null");
    Preconditions.checkNotNull(filename, "filename should not be null");

    XStream xStream = Helpers.xStream();
    File input = new File(path + "\\label_model_" + filename + ".xml.gz");

    return (U) xStream.fromXML(Files.compressedLineStream(input, StandardCharsets.UTF_8)
        .map(Map.Entry::getValue).collect(Collectors.joining("\n")));
  }

  /**
   * Compute correlation between each pair of labeling functions.
   *
   * @param goldLabels gold labels.
   * @param correlation correlation type.
   * @return a correlation matrix.
   */
  public Table<String, String, CorTest> labelingFunctionsCorrelations(
      List<? extends IGoldLabel<T>> goldLabels, Summary.eCorrelation correlation) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkNotNull(correlation, "correlation should not be null");

    return Summary.labelingFunctionsCorrelations(lfNames_, lfLabels_,
        Pipeline.on(goldLabels).transform(IGoldLabel::data).label(lfs_).collect(), correlation);
  }

  /**
   * Explore the labeling functions outputs.
   *
   * @param goldLabels gold labels.
   * @return a segmentation of the data according to the output produced by each labeling function.
   */
  public Table<String, Summary.eStatus, List<Map.Entry<T, FeatureVector<Integer>>>> explore(
      List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Summary.explore(lfNames_, lfLabels_,
        Pipeline.on(goldLabels).transform(IGoldLabel::data).label(lfs_).collect(),
        Pipeline.on(goldLabels).transform(gl -> lfLabels_.id(gl.label())).collect());
  }

  /**
   * Compute a {@link Summary} object with polarity, coverage, overlaps, etc. for each labeling
   * function. When gold labels are provided, this method will compute the number of correct and
   * incorrect labels output by each labeling function.
   *
   * @param goldLabels gold labels.
   * @return a {@link Summary} object for each labeling function.
   */
  public List<Summary> summarize(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Summary.summarize(lfNames_, lfLabels_,
        Pipeline.on(goldLabels).transform(IGoldLabel::data).label(lfs_).collect(),
        Pipeline.on(goldLabels).transform(gl -> lfLabels_.id(gl.label())).collect());
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
  public abstract void fit(List<? extends IGoldLabel<T>> goldLabels);

  /**
   * Make predictions.
   *
   * @param goldLabels gold labels.
   * @return output a prediction for each gold label.
   */
  public abstract List<Integer> predict(List<? extends IGoldLabel<T>> goldLabels);
}
