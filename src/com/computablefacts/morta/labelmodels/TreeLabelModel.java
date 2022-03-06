package com.computablefacts.morta.labelmodels;

import static com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.View;
import com.computablefacts.morta.*;
import com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

/**
 * This model is especially good when the labeling functions are highly correlated. Each labeling
 * function is weighted according to the label class : true positive, false positive, true negative
 * or false negative. Each labeling function MUST output a value in {ABSTAIN, OK, KO}.
 * 
 * @param <T> data type.
 */
@CheckReturnValue
final public class TreeLabelModel<T> extends AbstractLabelModel<T> {

  private final eMetric metric_;
  private Aggregate<T> tree_;

  public TreeLabelModel(TreeLabelModel<T> labelModel) {
    this(labelModel.lfs(), labelModel.metric_, labelModel.tree_);
  }

  public TreeLabelModel(List<? extends AbstractLabelingFunction<T>> lfs, eMetric metric) {

    super(lfsNames(lfs), lfsLabels(), lfs);

    Preconditions.checkNotNull(metric, "metric should not be null");

    metric_ = metric;
  }

  private TreeLabelModel(List<? extends AbstractLabelingFunction<T>> lfs, eMetric metric,
      Aggregate<T> tree) {

    super(lfsNames(lfs), lfsLabels(), lfs);

    Preconditions.checkNotNull(tree, "tree should not be null");
    Preconditions.checkNotNull(metric, "metric should not be null");

    tree_ = tree;
    metric_ = metric;
  }

  /**
   * Returns the binary class i.e. OK or KO, associated with a gold label.
   *
   * @param goldLabel gold label.
   * @return a binary class.
   */
  public static <T> int label(IGoldLabel<T> goldLabel) {

    Preconditions.checkNotNull(goldLabel, "goldLabel should not be null");

    return goldLabel.isTruePositive() || goldLabel.isFalseNegative() ? OK : KO;
  }

  private static <T> Dictionary lfsNames(List<? extends AbstractLabelingFunction<T>> lfs) {

    Preconditions.checkNotNull(lfs, "lfs should not be null");

    Dictionary lfNames = new Dictionary();

    for (int i = 0; i < lfs.size(); i++) {
      lfNames.put(lfs.get(i).name(), i);
    }
    return lfNames;
  }

  private static Dictionary lfsLabels() {

    Dictionary lfOutputs = new Dictionary();
    lfOutputs.put("KO", KO);
    lfOutputs.put("OK", OK);

    return lfOutputs;
  }

  @Override
  public String toString() {
    return tree_ == null ? "Invalid tree!" : tree_.toString();
  }

  @Override
  public Table<String, Summary.eStatus, List<Map.Entry<T, FeatureVector<Integer>>>> explore(
      List<IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Summary.explore(lfNames(), lfLabels(),
        View.of(goldLabels).map(IGoldLabel::data).map(Helpers.label(lfs())).toList(),
        View.of(goldLabels).map(TreeLabelModel::label).toList());
  }

  @Override
  public List<Summary> summarize(List<IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Summary.summarize(lfNames(), lfLabels(),
        View.of(goldLabels).map(IGoldLabel::data).map(Helpers.label(lfs())).toList(),
        View.of(goldLabels).map(TreeLabelModel::label).toList());
  }

  @Override
  public void fit(List<IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(
        goldLabels.stream().allMatch(gl -> gl.label().equals(goldLabels.get(0).label())),
        "gold labels must be identical");

    List<Aggregate<T>> aggregates1 = lfs().stream().map(lf -> new SimpleAggregate<>(lf, goldLabels))
        .collect(Collectors.toList());
    List<Aggregate<T>> aggregates2 = newAggregate(aggregates1, aggregates1);
    List<Aggregate<T>> aggregates3 = newAggregate(aggregates2, aggregates1);
    List<Aggregate<T>> aggregates4 = newAggregate(aggregates2, aggregates2);
    List<Aggregate<T>> aggregates5 = newAggregate(aggregates3, aggregates1);
    List<Aggregate<T>> aggregates6 = newAggregate(aggregates3, aggregates2);
    List<Aggregate<T>> aggregates7 = newAggregate(aggregates3, aggregates3);

    List<Aggregate<T>> aggregates = new ArrayList<>(aggregates1);
    aggregates.addAll(aggregates2);
    aggregates.addAll(aggregates3);
    aggregates.addAll(aggregates4);
    aggregates.addAll(aggregates5);
    aggregates.addAll(aggregates6);
    aggregates.addAll(aggregates7);
    aggregates.sort(Comparator.comparingDouble((Aggregate<T> a) -> eMetric.MCC.equals(metric_)
        ? a.confusionMatrix().matthewsCorrelationCoefficient()
        : a.confusionMatrix().f1Score()).reversed());

    if (!aggregates.isEmpty()) {
      tree_ = aggregates.get(0);
      tree_.reduce();
    }
  }

  /**
   * Make predictions. All predictions MUST BE in {OK, KO}.
   *
   * @param goldLabels gold labels.
   * @return output a prediction for each gold label.
   */
  @Override
  public List<Integer> predict(List<IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(
        goldLabels.stream().allMatch(gl -> gl.label().equals(goldLabels.get(0).label())),
        "gold labels must be identical");

    return goldLabels.stream().map(IGoldLabel::data).map(this::predict)
        .collect(Collectors.toList());
  }

  public eMetric metric() {
    return metric_;
  }

  public List<Map.Entry<T, FeatureVector<Integer>>> vectors(List<IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return View.of(goldLabels).map(IGoldLabel::data).map(Helpers.label(lfs())).toList();
  }

  public List<String> actual(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return View.of(goldLabels).map(TreeLabelModel::label)
        .map(pred -> labelingFunctionLabels().label(pred)).toList();
  }

  public List<String> predicted(List<IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return predict(goldLabels).stream()
        .map(pred -> pred == ABSTAIN ? "ABSTAIN" : labelingFunctionLabels().label(pred))
        .collect(Collectors.toList());
  }

  public ConfusionMatrix confusionMatrix(List<IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    List<Integer> actual =
        goldLabels.stream().map(TreeLabelModel::label).collect(Collectors.toList());
    List<Integer> predicted = predict(goldLabels);

    ConfusionMatrix matrix = new ConfusionMatrix();
    matrix.addAll(actual, predicted, OK, KO);

    return matrix;
  }

  private int predict(T data) {

    Preconditions.checkNotNull(data, "data should not be null");
    Preconditions.checkState(tree_ != null, "tree should not be null");

    return tree_.apply(data);
  }

  private List<Aggregate<T>> newAggregate(List<Aggregate<T>> aggregates1,
      List<Aggregate<T>> aggregates2) {

    Preconditions.checkNotNull(aggregates1, "aggregates1 should not be null");
    Preconditions.checkNotNull(aggregates2, "aggregates2 should not be null");

    List<Aggregate<T>> aggregates = new ArrayList<>();

    for (int i = 0; i < aggregates1.size(); i++) {
      for (int j = 0; j < aggregates2.size(); j++) {

        double firstMetric = eMetric.MCC.equals(metric_)
            ? aggregates1.get(i).confusionMatrix().matthewsCorrelationCoefficient()
            : aggregates1.get(i).confusionMatrix().f1Score();
        double secondMetric = eMetric.MCC.equals(metric_)
            ? aggregates2.get(j).confusionMatrix().matthewsCorrelationCoefficient()
            : aggregates2.get(j).confusionMatrix().f1Score();

        @Var
        Aggregate<T> aggregate = new AndAggregate<>(aggregates1.get(i), aggregates2.get(j));
        @Var
        double metric = eMetric.MCC.equals(metric_)
            ? aggregate.confusionMatrix().matthewsCorrelationCoefficient()
            : aggregate.confusionMatrix().f1Score();

        if (metric >= firstMetric || metric >= secondMetric) {
          aggregates.add(aggregate);
        }

        aggregate = new OrAggregate<>(aggregates1.get(i), aggregates2.get(j));
        metric = eMetric.MCC.equals(metric_)
            ? aggregate.confusionMatrix().matthewsCorrelationCoefficient()
            : aggregate.confusionMatrix().f1Score();

        if (metric >= firstMetric || metric >= secondMetric) {
          aggregates.add(aggregate);
        }

        aggregate = new AndNotAggregate<>(aggregates1.get(i), aggregates2.get(j));
        metric = eMetric.MCC.equals(metric_)
            ? aggregate.confusionMatrix().matthewsCorrelationCoefficient()
            : aggregate.confusionMatrix().f1Score();

        if (metric >= firstMetric || metric >= secondMetric) {
          aggregates.add(aggregate);
        }

        aggregate = new AndNotAggregate<>(aggregates2.get(j), aggregates1.get(i));
        metric = eMetric.MCC.equals(metric_)
            ? aggregate.confusionMatrix().matthewsCorrelationCoefficient()
            : aggregate.confusionMatrix().f1Score();

        if (metric >= firstMetric || metric >= secondMetric) {
          aggregates.add(aggregate);
        }
      }
    }
    return aggregates.parallelStream()
        .filter(aggregate -> Double.isFinite(eMetric.MCC.equals(metric_)
            ? aggregate.confusionMatrix().matthewsCorrelationCoefficient()
            : aggregate.confusionMatrix().f1Score()))
        .sorted(Comparator.comparingDouble((Aggregate<T> aggregate) -> eMetric.MCC.equals(metric_)
            ? aggregate.confusionMatrix().matthewsCorrelationCoefficient()
            : aggregate.confusionMatrix().f1Score()).reversed())
        .limit(100).collect(Collectors.toList());
  }

  public enum eMetric {
    F1, MCC
  }

  private interface Aggregate<T> extends Function<T, Integer> {

    List<Integer> actuals();

    List<Integer> predictions();

    ConfusionMatrix confusionMatrix();

    void reduce();
  }

  private static final class OrAggregate<T> implements Aggregate<T> {

    private final List<Integer> predictions_ = new ArrayList<>();
    private final ConfusionMatrix confusionMatrix_ = new ConfusionMatrix();
    private final Aggregate<T> aggregate1_;
    private final Aggregate<T> aggregate2_;

    public OrAggregate(Aggregate<T> aggregate1, Aggregate<T> aggregate2) {

      Preconditions.checkNotNull(aggregate1, "aggregate1 should not be null");
      Preconditions.checkNotNull(aggregate2, "aggregate2 should not be null");

      aggregate1_ = aggregate1;
      aggregate2_ = aggregate2;

      Preconditions.checkState(aggregate1.actuals().size() == aggregate2.actuals().size());
      Preconditions.checkState(aggregate1.predictions().size() == aggregate2.predictions().size());

      for (int i = 0; i < aggregate1.actuals().size(); i++) {

        int actual1 = aggregate1.actuals().get(i);
        int actual2 = aggregate2.actuals().get(i);

        Preconditions.checkState(actual1 == actual2);

        int prediction1 = aggregate1.predictions().get(i);
        int prediction2 = aggregate2.predictions().get(i);

        predictions_.add(reduce(prediction1, prediction2));
      }

      confusionMatrix_.addAll(aggregate1.actuals(), predictions_, OK, KO);
    }

    @Override
    public String toString() {
      return String.format("(%s OR %s)", aggregate1_.toString(), aggregate2_.toString());
    }

    @Override
    public List<Integer> actuals() {
      return aggregate1_.actuals();
    }

    @Override
    public List<Integer> predictions() {
      return predictions_;
    }

    @Override
    public ConfusionMatrix confusionMatrix() {
      return confusionMatrix_;
    }

    @Override
    public void reduce() {
      predictions_.clear();
      aggregate1_.reduce();
      aggregate2_.reduce();
    }

    @Override
    public Integer apply(@Nullable T input) {

      int prediction1 = aggregate1_.apply(input);
      int prediction2 = aggregate2_.apply(input);

      return reduce(prediction1, prediction2);
    }

    private int reduce(int prediction1, int prediction2) {
      return prediction1 == OK || prediction2 == OK ? OK : KO;
    }
  }

  private static final class AndAggregate<T> implements Aggregate<T> {

    private final List<Integer> predictions_ = new ArrayList<>();
    private final ConfusionMatrix confusionMatrix_ = new ConfusionMatrix();
    private final Aggregate<T> aggregate1_;
    private final Aggregate<T> aggregate2_;

    public AndAggregate(Aggregate<T> aggregate1, Aggregate<T> aggregate2) {

      Preconditions.checkNotNull(aggregate1, "aggregate1 should not be null");
      Preconditions.checkNotNull(aggregate2, "aggregate2 should not be null");

      aggregate1_ = aggregate1;
      aggregate2_ = aggregate2;

      Preconditions.checkState(aggregate1.actuals().size() == aggregate2.actuals().size());
      Preconditions.checkState(aggregate1.predictions().size() == aggregate2.predictions().size());

      for (int i = 0; i < aggregate1.actuals().size(); i++) {

        int actual1 = aggregate1.actuals().get(i);
        int actual2 = aggregate2.actuals().get(i);

        Preconditions.checkState(actual1 == actual2);

        int prediction1 = aggregate1.predictions().get(i);
        int prediction2 = aggregate2.predictions().get(i);

        predictions_.add(reduce(prediction1, prediction2));
      }

      confusionMatrix_.addAll(aggregate1.actuals(), predictions_, OK, KO);
    }

    @Override
    public String toString() {
      return String.format("(%s AND %s)", aggregate1_.toString(), aggregate2_.toString());
    }

    @Override
    public List<Integer> actuals() {
      return aggregate1_.actuals();
    }

    @Override
    public List<Integer> predictions() {
      return predictions_;
    }

    @Override
    public ConfusionMatrix confusionMatrix() {
      return confusionMatrix_;
    }

    @Override
    public void reduce() {
      predictions_.clear();
      aggregate1_.reduce();
      aggregate2_.reduce();
    }

    @Override
    public Integer apply(@Nullable T input) {

      int prediction1 = aggregate1_.apply(input);
      int prediction2 = aggregate2_.apply(input);

      return reduce(prediction1, prediction2);
    }

    private int reduce(int prediction1, int prediction2) {
      return prediction1 == OK && prediction2 == OK ? OK : KO;
    }
  }

  private static final class AndNotAggregate<T> implements Aggregate<T> {

    private final List<Integer> predictions_ = new ArrayList<>();
    private final ConfusionMatrix confusionMatrix_ = new ConfusionMatrix();
    private final Aggregate<T> aggregate1_;
    private final Aggregate<T> aggregate2_;

    public AndNotAggregate(Aggregate<T> aggregate1, Aggregate<T> aggregate2) {

      Preconditions.checkNotNull(aggregate1, "aggregate1 should not be null");
      Preconditions.checkNotNull(aggregate2, "aggregate2 should not be null");

      aggregate1_ = aggregate1;
      aggregate2_ = aggregate2;

      Preconditions.checkState(aggregate1.actuals().size() == aggregate2.actuals().size());
      Preconditions.checkState(aggregate1.predictions().size() == aggregate2.predictions().size());

      for (int i = 0; i < aggregate1.actuals().size(); i++) {

        int actual1 = aggregate1.actuals().get(i);
        int actual2 = aggregate2.actuals().get(i);

        Preconditions.checkState(actual1 == actual2);

        int prediction1 = aggregate1.predictions().get(i);
        int prediction2 = aggregate2.predictions().get(i);

        predictions_.add(reduce(prediction1, prediction2));
      }

      confusionMatrix_.addAll(aggregate1.actuals(), predictions_, OK, KO);
    }

    @Override
    public String toString() {
      return String.format("(%s AND NOT %s)", aggregate1_.toString(), aggregate2_.toString());
    }

    @Override
    public List<Integer> actuals() {
      return aggregate1_.actuals();
    }

    @Override
    public List<Integer> predictions() {
      return predictions_;
    }

    @Override
    public ConfusionMatrix confusionMatrix() {
      return confusionMatrix_;
    }

    @Override
    public void reduce() {
      predictions_.clear();
      aggregate1_.reduce();
      aggregate2_.reduce();
    }

    @Override
    public Integer apply(@Nullable T input) {

      int prediction1 = aggregate1_.apply(input);
      int prediction2 = aggregate2_.apply(input);

      return reduce(prediction1, prediction2);
    }

    private int reduce(int prediction1, int prediction2) {
      return prediction1 == OK && (prediction2 == KO || prediction2 == ABSTAIN) ? OK : KO;
    }
  }

  private static final class SimpleAggregate<T> implements Aggregate<T> {

    private final AbstractLabelingFunction<T> labelingFunction_;
    private final List<Integer> actuals_ = new ArrayList<>();
    private final List<Integer> predictions_ = new ArrayList<>();
    private final ConfusionMatrix confusionMatrix_ = new ConfusionMatrix();

    public SimpleAggregate(AbstractLabelingFunction<T> labelingFunction,
        List<? extends IGoldLabel<T>> goldLabels) {

      Preconditions.checkNotNull(labelingFunction, "labelingFunction should not be null");
      Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

      labelingFunction_ = labelingFunction;

      goldLabels.forEach(goldLabel -> {

        int actual = label(goldLabel);
        int prediction = apply(goldLabel.data());

        actuals_.add(actual);
        predictions_.add(prediction);
      });

      confusionMatrix_.addAll(actuals_, predictions_, OK, KO);
    }

    @Override
    public String toString() {
      return labelingFunction_.name();
    }

    @Override
    public List<Integer> actuals() {
      return actuals_;
    }

    @Override
    public List<Integer> predictions() {
      return predictions_;
    }

    @Override
    public ConfusionMatrix confusionMatrix() {
      return confusionMatrix_;
    }

    @Override
    public void reduce() {
      predictions_.clear();
      actuals_.clear();
    }

    @Override
    public Integer apply(@Nullable T input) {
      int prediction = labelingFunction_.apply(input);
      return prediction == OK ? OK : KO;
    }
  }
}
