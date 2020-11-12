package com.computablefacts.morta.poc;

import static com.computablefacts.morta.snorkel.ILabelingFunction.ABSTAIN;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.computablefacts.morta.Pipeline;
import com.computablefacts.morta.snorkel.AbstractLabelModel;
import com.computablefacts.morta.snorkel.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.ILabelingFunction;
import com.computablefacts.morta.snorkel.Summary;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.primitives.Doubles;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

import smile.stat.hypothesis.CorTest;

/**
 * This model is especially good when all gold labels share the same label. Each labeling function
 * is weighted according to the label class : true positive, false positive, true negative or false
 * negative. Each labeling function MUST output a value in {ABSTAIN, LABEL_OK, LABEL_KO}.
 * 
 * @param <T> data type.
 */
@CheckReturnValue
final public class MedianLabelModel<T> extends AbstractLabelModel<T> {

  public static final int LABEL_KO = 0;
  public static final int LABEL_OK = 1;

  private List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries_;
  private List<Integer> lfsOk_;
  private List<Integer> lfsKo_;
  private Set<Integer> lfsShared_;
  private double avgNbOfLfsTriggeredOk_;
  private double avgNbOfLfsTriggeredKo_;
  private double avgNbOfBestLfsTriggeredOk_;
  private double avgNbOfBestLfsTriggeredKo_;

  public MedianLabelModel(MedianLabelModel<T> labelModel) {
    this(labelModel.lfs(), labelModel.lfSummaries(), labelModel.lfsOk(), labelModel.lfsKo(),
        labelModel.lfsShared());
  }

  public MedianLabelModel(List<? extends AbstractLabelingFunction<T>> lfs) {
    super(lfsNames(lfs), lfsLabels(), lfs);
  }

  private MedianLabelModel(List<? extends AbstractLabelingFunction<T>> lfs,
      List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries,
      List<Integer> lfsOk, List<Integer> lfsKo, Set<Integer> lfsShared) {

    super(lfsNames(lfs), lfsLabels(), lfs);

    Preconditions.checkNotNull(lfSummaries, "lfSummaries should not be null");
    Preconditions.checkNotNull(lfsOk, "lfsOk should not be null");
    Preconditions.checkNotNull(lfsKo, "lfsKo should not be null");
    Preconditions.checkNotNull(lfsShared, "lfsShared should not be null");

    lfSummaries_ = new ArrayList<>(lfSummaries);
    lfsOk_ = new ArrayList<>(lfsOk);
    lfsKo_ = new ArrayList<>(lfsKo);
    lfsShared_ = new HashSet<>(lfsShared);
  }

  /**
   * Returns the binary class i.e. LABEL_OK or LABEL_KO, associated with a gold label.
   *
   * @param goldLabel gold label.
   * @return a binary class.
   */
  static <T> int label(IGoldLabel<T> goldLabel) {

    Preconditions.checkNotNull(goldLabel, "goldLabel should not be null");

    return goldLabel.isTruePositive() || goldLabel.isFalseNegative() ? LABEL_OK : LABEL_KO;
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
    lfOutputs.put("KO", LABEL_KO);
    lfOutputs.put("OK", LABEL_OK);

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
        Pipeline.on(goldLabels).transform(MedianLabelModel::label).collect());
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
        Pipeline.on(goldLabels).transform(MedianLabelModel::label).collect());
  }

  @Override
  public void fit(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(
        goldLabels.stream().allMatch(gl -> gl.label().equals(goldLabels.get(0).label())),
        "gold labels must be identical");

    // Map each LF to its summary
    List<Summary> summaries = summarize(goldLabels);

    lfSummaries_ = lfs().stream().map(lf -> {

      Optional<Summary> summary =
          summaries.stream().filter(s -> s.label().equals(lf.name())).findFirst();

      Preconditions.checkState(summary.isPresent(),
          "Inconsistent state reached between LF and Summaries");

      return new AbstractMap.SimpleEntry<>(lf, summary.get());
    }).collect(Collectors.toList());

    // Weight each LF
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
    List<List<Double>> scoresOk = new ArrayList<>(goldLabels.size());
    List<List<Double>> scoresKo = new ArrayList<>(goldLabels.size());
    List<List<Double>> scoresAbs = new ArrayList<>(goldLabels.size());

    for (int i = 0; i < goldLabels.size(); i++) {

      bar.update(i, goldLabels.size(), "Weighting labeling functions...");

      IGoldLabel<T> goldLabel = goldLabels.get(i);
      T data = goldLabel.data();
      int label = label(goldLabel);

      List<Double> ok = score(lfSummaries_, LABEL_OK, data);
      List<Double> ko = score(lfSummaries_, LABEL_KO, data);
      List<Double> abs = score(lfSummaries_, ABSTAIN, data);

      if (label == LABEL_OK) {
        scoresOk.add(ok);
      } else if (label == LABEL_KO) {
        scoresKo.add(ko);
      } else {
        scoresAbs.add(abs);
      }
    }

    lfsOk_ = bestLfsForTagging(scoresOk);
    lfsKo_ = bestLfsForTagging(scoresKo);
    lfsShared_ = Sets.intersection(Sets.newHashSet(lfsOk_), Sets.newHashSet(lfsKo_));

    avgNbOfLfsTriggeredOk_ = avgNbOfLfsTriggered(scoresOk);
    avgNbOfLfsTriggeredKo_ = avgNbOfLfsTriggered(scoresKo);

    avgNbOfBestLfsTriggeredOk_ = avgNbOfBestLfsTriggered(scoresOk, lfsOk_);
    avgNbOfBestLfsTriggeredKo_ = avgNbOfBestLfsTriggered(scoresKo, lfsKo_);
  }

  @Override
  public List<Integer> predict(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(
        goldLabels.stream().allMatch(gl -> gl.label().equals(goldLabels.get(0).label())),
        "gold labels must be identical");

    return goldLabels.stream().map(IGoldLabel::data).map(data -> predict(data))
        .collect(Collectors.toList());
  }

  public List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries() {
    return lfSummaries_;
  }

  public List<Integer> lfsOk() {
    return lfsOk_;
  }

  public List<Integer> lfsKo() {
    return lfsKo_;
  }

  public Set<Integer> lfsShared() {
    return lfsShared_;
  }

  public List<Map.Entry<T, FeatureVector<Integer>>> vectors(
      List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Pipeline.on(goldLabels).transform(IGoldLabel::data).label(lfs()).collect();
  }

  public List<String> actual(List<? extends IGoldLabel<T>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");

    return Pipeline.on(goldLabels).transform(MedianLabelModel::label)
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
        goldLabels.stream().map(MedianLabelModel::label).collect(Collectors.toList());
    List<Integer> predicted = predict(goldLabels);

    ConfusionMatrix matrix = new ConfusionMatrix();
    matrix.addAll(actual, predicted, LABEL_OK, LABEL_KO);

    return matrix;
  }

  private List<Double> score(
      List<Map.Entry<? extends AbstractLabelingFunction<T>, Summary>> lfSummaries, int label,
      T data) {

    Preconditions.checkNotNull(lfSummaries, "lfSummaries should not be null");
    Preconditions.checkArgument(label == ABSTAIN || label == LABEL_OK || label == LABEL_KO,
        "unknown label class");
    Preconditions.checkNotNull(data, "data should not be null");

    List<Double> vector = new ArrayList<>();

    for (Map.Entry<? extends ILabelingFunction<T>, Summary> lfSummary : lfSummaries) {

      ILabelingFunction<T> lf = lfSummary.getKey();
      // Summary summary = lfSummary.getValue();

      if (lf.apply(data) == label) {
        vector.add(1.0);
      } else {
        vector.add(0.0);
      }
    }
    return vector;
  }

  private List<Integer> bestLfsForTagging(List<List<Double>> scores) {

    Preconditions.checkNotNull(scores, "scores should not be null");

    // Get the list of triggered LF for each instance
    List<Map.Entry<Integer, Set<Integer>>> lfsTriggered = new ArrayList<>();

    for (int i = 0; i < scores.size(); i++) {

      Set<Integer> lfs = new HashSet<>();

      for (int j = 0; j < scores.get(i).size(); j++) {
        if (scores.get(i).get(j) > 0) {
          lfs.add(j);
        }
      }

      lfsTriggered.add(new AbstractMap.SimpleEntry<>(i, lfs));
    }

    lfsTriggered.sort((o1, o2) -> Doubles.compare(o1.getValue().size(), o2.getValue().size()));

    // Get the list of tagged instances for each LF
    List<Map.Entry<Integer, Set<Integer>>> instancesTagged = new ArrayList<>();

    for (int i = 0; i < scores.get(0).size(); i++) {

      Set<Integer> instances = new HashSet<>();

      for (int j = 0; j < scores.size(); j++) {
        if (scores.get(j).get(i) > 0) {
          instances.add(j);
        }
      }

      instancesTagged.add(new AbstractMap.SimpleEntry<>(i, instances));
    }

    instancesTagged.sort((o1, o2) -> Doubles.compare(o1.getValue().size(), o2.getValue().size()));

    // Find the minimum number of LF needed to tag all instances
    @Var
    int subset = -1;

    for (int i = 0; i < instancesTagged.size(); i++) {

      @Var
      Set<Integer> instances = new HashSet<>();

      for (int j = 0; j <= i; j++) {
        instances = Sets.union(instances, instancesTagged.get(j).getValue());
      }

      if (instances.size() == scores.size()) {
        subset = i;
        break;
      }
    }
    return subset <= 0 ? new ArrayList<>()
        : instancesTagged.stream().limit(subset).map(i -> i.getKey()).collect(Collectors.toList());
  }

  private double avgNbOfBestLfsTriggered(List<List<Double>> scores,
      List<Integer> bestLfsForTagging) {

    Preconditions.checkNotNull(scores, "scores should not be null");
    Preconditions.checkNotNull(bestLfsForTagging, "bestLfsForTagging should not be null");

    List<Double> nbOfLfsTriggered = new ArrayList<>();

    for (int i = 0; i < scores.size(); i++) {

      @Var
      double sum = 0.0;

      for (int j = 0; j < scores.get(i).size(); j++) {
        if (scores.get(i).get(j) > 0 && bestLfsForTagging.contains(j)) {
          sum += 1.0;
        }
      }

      nbOfLfsTriggered.add(sum);
    }
    return nbOfLfsTriggered.stream().mapToDouble(d -> d).average().orElse(0.0);
  }

  private double avgNbOfLfsTriggered(List<List<Double>> scores) {

    Preconditions.checkNotNull(scores, "scores should not be null");

    // Get the list of triggered LF for each instance
    List<Map.Entry<Integer, Set<Integer>>> lfsTriggered = new ArrayList<>();

    for (int i = 0; i < scores.size(); i++) {

      Set<Integer> lfs = new HashSet<>();

      for (int j = 0; j < scores.get(i).size(); j++) {
        if (scores.get(i).get(j) > 0) {
          lfs.add(j);
        }
      }

      lfsTriggered.add(new AbstractMap.SimpleEntry<>(i, lfs));
    }

    lfsTriggered.sort((o1, o2) -> Doubles.compare(o1.getValue().size(), o2.getValue().size()));

    // Find the average number of triggered LF
    return lfsTriggered.stream().mapToDouble(lfs -> lfs.getValue().size()).average().orElse(0.0);
  }

  private int predict(T data) {

    Preconditions.checkNotNull(data, "data should not be null");

    List<Double> ok = score(lfSummaries_, LABEL_OK, data);
    List<Double> ko = score(lfSummaries_, LABEL_KO, data);

    List<Integer> lfsOk = new ArrayList<>();

    for (int i = 0; i < ok.size(); i++) {
      if (ok.get(i) > 0 && lfsOk_.contains(i)) {
        lfsOk.add(i);
      }
    }

    List<Integer> lfsKo = new ArrayList<>();

    for (int i = 0; i < ko.size(); i++) {
      if (ko.get(i) > 0 && lfsKo_.contains(i)) {
        lfsKo.add(i);
      }
    }

    if (!lfsOk.isEmpty() && lfsKo.isEmpty()) {
      return LABEL_OK;
    }
    if (lfsOk.isEmpty() && !lfsKo.isEmpty()) {
      return LABEL_KO;
    }

    Set<Integer> lfsOkMinusShared = Sets.difference(Sets.newHashSet(lfsOk), lfsShared_);
    Set<Integer> lfsKoMinusShared = Sets.difference(Sets.newHashSet(lfsKo), lfsShared_);

    double distOk = Math.abs(ok.stream().mapToDouble(d -> d).sum() - avgNbOfLfsTriggeredOk_);
    double distKo = Math.abs(ko.stream().mapToDouble(d -> d).sum() - avgNbOfLfsTriggeredKo_);

    double distLfsOk = Math.abs(lfsOk.size() - avgNbOfBestLfsTriggeredOk_);
    double distLfsKo = Math.abs(lfsKo.size() - avgNbOfBestLfsTriggeredKo_);

    if (!lfsOkMinusShared.isEmpty() && lfsKoMinusShared.isEmpty()) {
      return distOk < distKo ? LABEL_OK : LABEL_KO;
    }
    if (lfsOkMinusShared.isEmpty() && !lfsKoMinusShared.isEmpty()) {
      return distOk > distKo ? LABEL_KO : LABEL_OK;
    }
    return lfsOkMinusShared.size() > lfsKoMinusShared.size() && distOk < distKo
        && distLfsOk < distLfsKo ? LABEL_OK : LABEL_KO;
  }
}
