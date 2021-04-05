package com.computablefacts.morta.snorkel.labelmodels;

import static com.computablefacts.morta.snorkel.ILabelingFunction.ABSTAIN;
import static com.computablefacts.morta.snorkel.ILabelingFunction.KO;
import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.Pipeline;
import com.computablefacts.morta.snorkel.Summary;
import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.nona.Generated;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

import smile.stat.hypothesis.CorTest;

/**
 * This model is especially good when the labeling functions are highly correlated. Each labeling
 * function is weighted according to the label class : true positive, false positive, true negative
 * or false negative. Each labeling function MUST output a value in {ABSTAIN, OK, KO}.
 * 
 * @param <T> data type.
 */
@CheckReturnValue
final public class TreeLabelModel<T> extends AbstractLabelModel<T> {

  private List<Summary> lfSummaries_;
  private Aggregate<T> tree_;

  public TreeLabelModel(TreeLabelModel<T> labelModel) {
    this(labelModel.lfs(), labelModel.lfSummaries(), labelModel.tree_);
  }

  public TreeLabelModel(List<? extends AbstractLabelingFunction<T>> lfs) {
    super(lfsNames(lfs), lfsLabels(), lfs);
  }

  private TreeLabelModel(List<? extends AbstractLabelingFunction<T>> lfs, List<Summary> lfSummaries,
      Aggregate<T> tree) {

    super(lfsNames(lfs), lfsLabels(), lfs);

    Preconditions.checkNotNull(lfSummaries, "lfSummaries should not be null");
    Preconditions.checkNotNull(tree, "tree should not be null");

    lfSummaries_ = new ArrayList<>(lfSummaries);
    tree_ = tree;
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
  public Table<String, String, CorTest> labelingFunctionsCorrelations(
      List<? extends IGoldLabel<T>> goldLabels, Summary.eCorrelation correlation) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkNotNull(correlation, "correlation should not be null");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

    return Summary.labelingFunctionsCorrelations(lfNames(), lfLabels(),
        Pipeline.on(goldLabels).transform(IGoldLabel::data)
            .peek(d -> bar.update(count.incrementAndGet(), goldLabels.size(), "Correlating..."))
            .label(lfs()).collect(),
        correlation);
  }

  @Override
  public Table<String, Summary.eStatus, List<Map.Entry<T, FeatureVector<Integer>>>> explore(
      List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

    return Summary.explore(lfNames(), lfLabels(),
        Pipeline.on(goldLabels).transform(IGoldLabel::data)
            .peek(d -> bar.update(count.incrementAndGet(), goldLabels.size(), "Exploring..."))
            .label(lfs()).collect(),
        Pipeline.on(goldLabels).transform(TreeLabelModel::label).collect());
  }

  @Override
  public List<Summary> summarize(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

    return Summary.summarize(lfNames(), lfLabels(),
        Pipeline.on(goldLabels).transform(IGoldLabel::data)
            .peek(d -> bar.update(count.incrementAndGet(), goldLabels.size(), "Summarizing..."))
            .label(lfs()).collect(),
        Pipeline.on(goldLabels).transform(TreeLabelModel::label).collect());
  }

  @Override
  public void fit(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(
        goldLabels.stream().allMatch(gl -> gl.label().equals(goldLabels.get(0).label())),
        "gold labels must be identical");

    // Map each LF to its summary
    lfSummaries_ = summarize(goldLabels);

    List<Aggregate<T>> simpleAggregates = lfs().stream().map(lf -> {

      Optional<Summary> summary =
          lfSummaries_.stream().filter(s -> s.label().equals(lf.name())).findFirst();

      Preconditions.checkState(summary.isPresent(),
          "Inconsistent state reached between LF and Summaries");

      return new SimpleAggregate<>(lf, goldLabels);
    }).sorted(Comparator.comparingDouble((SimpleAggregate<T> s) -> s.confusionMatrix_.accuracy())
        .reversed()).collect(Collectors.toList());

    @Var
    List<Aggregate<T>> aggregates = newAggregate(simpleAggregates, simpleAggregates);
    tree_ = aggregates.get(0);

    while (true) {

      aggregates = newAggregate(aggregates, simpleAggregates,
          tree_.confusionMatrix().matthewsCorrelationCoefficient());

      if (tree_.confusionMatrix().matthewsCorrelationCoefficient() < aggregates.get(0)
          .confusionMatrix().matthewsCorrelationCoefficient()) {
        tree_ = aggregates.get(0);
      } else {
        break;
      }
    }

    tree_.reduce();
  }

  /**
   * Make predictions. All predictions MUST BE in {OK, KO}.
   *
   * @param goldLabels gold labels.
   * @return output a prediction for each gold label.
   */
  @Override
  public List<Integer> predict(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(
        goldLabels.stream().allMatch(gl -> gl.label().equals(goldLabels.get(0).label())),
        "gold labels must be identical");

    return goldLabels.stream().map(IGoldLabel::data).map(this::predict)
        .collect(Collectors.toList());
  }

  @Generated
  public List<Summary> lfSummaries() {
    return lfSummaries_;
  }

  public List<Map.Entry<T, FeatureVector<Integer>>> vectors(
      List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Pipeline.on(goldLabels).transform(IGoldLabel::data).label(lfs()).collect();
  }

  public List<String> actual(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Pipeline.on(goldLabels).transform(TreeLabelModel::label)
        .transform(pred -> labelingFunctionLabels().label(pred)).collect();
  }

  public List<String> predicted(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return predict(goldLabels).stream()
        .map(pred -> pred == ABSTAIN ? "ABSTAIN" : labelingFunctionLabels().label(pred))
        .collect(Collectors.toList());
  }

  public ConfusionMatrix confusionMatrix(List<? extends IGoldLabel<T>> goldLabels) {

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

    return tree_.apply(data);
  }

  private List<Aggregate<T>> newAggregate(List<Aggregate<T>> aggregates1,
      List<Aggregate<T>> aggregates2) {

    Preconditions.checkNotNull(aggregates1, "aggregates1 should not be null");
    Preconditions.checkNotNull(aggregates2, "aggregates2 should not be null");

    List<Aggregate<T>> aggregates = new ArrayList<>();

    for (int i = 0; i < aggregates1.size(); i++) {
      for (int j = 0; j < aggregates2.size(); j++) {
        aggregates.add(new AndAggregate<>(aggregates1.get(i), aggregates2.get(j)));
        aggregates.add(new OrAggregate<>(aggregates1.get(i), aggregates2.get(j)));
        aggregates.add(new AndNotAggregate<>(aggregates1.get(i), aggregates2.get(j)));
      }
    }
    return aggregates.stream().filter(
        aggregate -> Double.isFinite(aggregate.confusionMatrix().matthewsCorrelationCoefficient()))
        .sorted(Comparator.comparingDouble((Aggregate<T> aggregate) -> aggregate.confusionMatrix()
            .matthewsCorrelationCoefficient()).reversed())
        .collect(Collectors.toList());
  }

  private List<Aggregate<T>> newAggregate(List<Aggregate<T>> aggregates1,
      List<Aggregate<T>> aggregates2, double mccCutOff) {

    Preconditions.checkNotNull(aggregates1, "aggregates1 should not be null");
    Preconditions.checkNotNull(aggregates2, "aggregates2 should not be null");

    List<Aggregate<T>> aggregates = new ArrayList<>();

    for (int i = 0; i < aggregates1.size(); i++) {
      for (int j = 0; j < aggregates2.size(); j++) {

        Aggregate<T> aggregate1 = new AndAggregate<>(aggregates1.get(i), aggregates2.get(j));

        if (aggregate1.confusionMatrix().matthewsCorrelationCoefficient() >= mccCutOff) {
          aggregates.add(aggregate1);
        }

        Aggregate<T> aggregate2 = new OrAggregate<>(aggregates1.get(i), aggregates2.get(j));

        if (aggregate2.confusionMatrix().matthewsCorrelationCoefficient() >= mccCutOff) {
          aggregates.add(aggregate2);
        }

        Aggregate<T> aggregate3 = new AndNotAggregate<>(aggregates1.get(i), aggregates2.get(j));

        if (aggregate3.confusionMatrix().matthewsCorrelationCoefficient() >= mccCutOff) {
          aggregates.add(aggregate3);
        }
      }
    }
    return aggregates.stream().filter(
        aggregate -> Double.isFinite(aggregate.confusionMatrix().matthewsCorrelationCoefficient()))
        .sorted(Comparator.comparingDouble((Aggregate<T> aggregate) -> aggregate.confusionMatrix()
            .matthewsCorrelationCoefficient()).reversed())
        .collect(Collectors.toList());
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

        predictions_.add(prediction1 == OK || prediction2 == OK ? OK : KO);
      }

      confusionMatrix_.addAll(aggregate1.actuals(), predictions_, OK, KO);
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
    public @Nullable Integer apply(@Nullable T input) {
      return aggregate1_.apply(input) == OK || aggregate2_.apply(input) == OK ? OK : KO;
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

        predictions_.add(prediction1 == OK && prediction2 == OK ? OK : KO);
      }

      confusionMatrix_.addAll(aggregate1.actuals(), predictions_, OK, KO);
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
    public @Nullable Integer apply(@Nullable T input) {
      return aggregate1_.apply(input) == OK && aggregate2_.apply(input) == OK ? OK : KO;
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

        predictions_.add(prediction1 == OK && prediction2 != OK ? OK : KO);
      }

      confusionMatrix_.addAll(aggregate1.actuals(), predictions_, OK, KO);
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
    public @Nullable Integer apply(@Nullable T input) {
      return aggregate1_.apply(input) == OK && aggregate2_.apply(input) != OK ? OK : KO;
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
        int prediction = labelingFunction.apply(goldLabel.data());

        actuals_.add(actual);
        predictions_.add(prediction);
      });

      confusionMatrix_.addAll(actuals_, predictions_, OK, KO);
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
    public @Nullable Integer apply(@Nullable T input) {
      return labelingFunction_.apply(input);
    }
  }
}
