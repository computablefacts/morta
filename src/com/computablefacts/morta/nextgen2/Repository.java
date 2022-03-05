package com.computablefacts.morta.nextgen2;

import static com.computablefacts.morta.nextgen.GoldLabelsRepository.ACCEPT;
import static com.computablefacts.morta.nextgen.GoldLabelsRepository.REJECT;
import static com.computablefacts.morta.snorkel.IGoldLabel.SANITIZE_SNIPPET;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.computablefacts.asterix.View;
import com.computablefacts.morta.docsetlabeler.DocSetLabelerImpl;
import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.labelingfunctions.MatchRegexLabelingFunction;
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
import com.computablefacts.morta.textcat.FingerPrint;
import com.computablefacts.morta.textcat.TextCategorizer;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
public final class Repository {

  private final String outputDir_;
  private boolean isInitialized_ = false;

  public Repository(String outputDir) {
    outputDir_ = Preconditions.checkNotNull(outputDir, "outputDir should not be null");
  }

  /**
   * Initialize the current repository.
   *
   * @param facts the facts.
   * @param documents the facts' underlying documents.
   * @param withProgressBar true iif a progress bar should be displayed, false otherwise.
   * @return a set of fact types i.e. labels.
   */
  public Set<String> init(File facts, File documents, boolean withProgressBar) {

    Preconditions.checkState(!isInitialized_, "init should be called only once");

    isInitialized_ = true;

    return View.of(pagesAsGoldLabels(facts, documents, withProgressBar))
        .concat(View.of(factsAsGoldLabels(facts, documents, withProgressBar)))
        .map(IGoldLabel::label).toSet();
  }

  /**
   * Returns all the gold labels found in the current repository.
   *
   * @return the list of gold labels.
   */
  public Set<String> labels() {

    Preconditions.checkState(isInitialized_, "init should be called first");

    return View.of(pagesAsGoldLabels(null, null, false))
        .concat(View.of(factsAsGoldLabels(null, null, false))).map(IGoldLabel::label).toSet();
  }

  /**
   * Load facts and documents for a given label.
   *
   * @param label the label to load. If {@code label} is {@code null}, load all facts and documents.
   * @return a set of facts and documents.
   */
  public Set<FactAndDocument> factsAndDocuments(String label) {

    Preconditions.checkState(isInitialized_, "init should be called first");

    return FactAndDocument.load(fileFactsAndDocuments(), null, false).stream()
        .filter(goldLabel -> label == null || label.equals(goldLabel.label()))
        .collect(Collectors.toSet());
  }

  /**
   * Load pages as gold labels for a given label.
   *
   * @param label the label to load. If {@code label} is {@code null}, load all pages as gold
   *        labels.
   * @return a set of gold labels.
   */
  public Set<IGoldLabel<String>> pagesAsGoldLabels(String label) {

    Preconditions.checkState(isInitialized_, "init should be called first");

    return GoldLabelOfString.load(filePagesAsGoldLabels(), null, false).stream()
        .filter(goldLabel -> label == null || label.equals(goldLabel.label()))
        .collect(Collectors.toSet());

  }

  /**
   * Load facts as gold labels for a given label.
   *
   * @param label the label to load. If {@code label} is {@code null}, load all facts as gold
   *        labels.
   * @return a set of gold labels.
   */
  public Set<IGoldLabel<String>> factsAsGoldLabels(String label) {

    Preconditions.checkState(isInitialized_, "init should be called first");

    return GoldLabelOfString.load(fileFactsAsGoldLabels(), null, false).stream()
        .filter(goldLabel -> label == null || label.equals(goldLabel.label()))
        .collect(Collectors.toSet());
  }

  /**
   * Create a {@link TextCategorizer}.
   *
   * The returned category is the fact type i.e. gold label's label.
   *
   * @return a {@link TextCategorizer}.
   */
  public TextCategorizer textCategorizer() {

    Preconditions.checkState(isInitialized_, "init should be called first");

    File file = fileTextCategorizer();

    if (file.exists()) {
      return Helpers.deserialize(file.getAbsolutePath());
    }

    TextCategorizer textCategorizer = new TextCategorizer();
    labels().forEach(label -> {

      Set<IGoldLabel<String>> goldLabelsAccepted = factsAsGoldLabels(label).stream()
          .filter(goldLabel -> goldLabel.isTruePositive() || goldLabel.isFalseNegative())
          .collect(Collectors.toSet());

      StringBuilder accepted = new StringBuilder();
      double avgLengthAccepted = goldLabelsAccepted.stream()
          .peek(goldLabel -> accepted.append(sanitize(goldLabel.data())).append("\n\n\n"))
          .mapToInt(goldLabel -> goldLabel.data().length()).average().orElse(0);

      FingerPrint fingerPrint = new FingerPrint();
      fingerPrint.category(label);
      fingerPrint.avgLength(avgLengthAccepted);
      fingerPrint.create(accepted.toString());

      textCategorizer.add(fingerPrint);
    });

    Helpers.serialize(file.getAbsolutePath(), textCategorizer);
    return textCategorizer;
  }

  /**
   * Create a {@link TextCategorizer} for a given gold label.
   *
   * The returned categories are either {@code ACCEPT} or {@code REJECT}.
   *
   * @param label the label to load.
   * @return a {@link TextCategorizer}.
   */
  public TextCategorizer textCategorizer(String label) {

    Preconditions.checkState(isInitialized_, "init should be called first");
    Preconditions.checkNotNull(label, "label should not be null");

    File file = fileTextCategorizer(label);

    if (file.exists()) {
      return Helpers.deserialize(file.getAbsolutePath());
    }

    Set<IGoldLabel<String>> goldLabels = factsAsGoldLabels(label);
    Set<IGoldLabel<String>> goldLabelsAccepted = goldLabels.stream()
        .filter(goldLabel -> goldLabel.isTruePositive() || goldLabel.isFalseNegative())
        .collect(Collectors.toSet());
    Set<IGoldLabel<String>> goldLabelsRejected = goldLabels.stream()
        .filter(goldLabel -> !goldLabel.isTruePositive() && !goldLabel.isFalseNegative())
        .collect(Collectors.toSet());

    StringBuilder accepted = new StringBuilder();
    double avgLengthAccepted = goldLabelsAccepted.stream()
        .peek(goldLabel -> accepted.append(sanitize(goldLabel.data())).append("\n\n\n"))
        .mapToInt(goldLabel -> goldLabel.data().length()).average().orElse(0);

    StringBuilder rejected = new StringBuilder();
    double avgLengthRejected = goldLabelsRejected.stream()
        .peek(goldLabel -> rejected.append(sanitize(goldLabel.data())).append("\n\n\n"))
        .mapToInt(goldLabel -> goldLabel.data().length()).average().orElse(0);

    FingerPrint fpAccepted = new FingerPrint();
    fpAccepted.category(ACCEPT);
    fpAccepted.avgLength(avgLengthAccepted);
    fpAccepted.create(accepted.toString());

    FingerPrint fpRejected = new FingerPrint();
    fpRejected.category(REJECT);
    fpRejected.avgLength(avgLengthRejected);
    fpRejected.create(rejected.toString());

    TextCategorizer textCategorizer = new TextCategorizer();
    textCategorizer.add(fpAccepted);
    textCategorizer.add(fpRejected);

    Helpers.serialize(file.getAbsolutePath(), textCategorizer);
    return textCategorizer;
  }

  /**
   * Load or guesstimate labeling functions using the
   * {@link com.computablefacts.morta.docsetlabeler.DocSetLabeler} algorithm.
   *
   * @param label the label for which the labeling functions must be guesstimated.
   * @param maxGroupSize the maximum number of tokens for a single ngram.
   * @param nbCandidatesToConsider number of candidate labels the
   *        {@link com.computablefacts.morta.docsetlabeler.DocSetLabeler} algorithm should consider
   *        on each iteration.
   * @param nbLabelsToReturn number of labels the
   *        {@link com.computablefacts.morta.docsetlabeler.DocSetLabeler} algorithm should return at
   *        the end.
   * @return a list of labeling functions.
   */
  public List<AbstractLabelingFunction<String>> labelingFunctions(String label, int maxGroupSize,
      int nbCandidatesToConsider, int nbLabelsToReturn) {

    Preconditions.checkState(isInitialized_, "init should be called first");
    Preconditions.checkNotNull(label, "label should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");
    Preconditions.checkArgument(nbCandidatesToConsider > 0, "nbCandidatesToConsider must be > 0");
    Preconditions.checkArgument(nbLabelsToReturn > 0, "nbLabelsToReturn must be > 0");

    File file = fileGuesstimatedLabelingFunctions(label);

    if (file.exists()) {
      return Helpers.deserialize(file.getAbsolutePath());
    }

    TextCategorizer textCategorizer = textCategorizer(label);
    double avgFingerPrintLength = textCategorizer.categories().stream()
        .filter(fingerPrint -> ACCEPT.equals(fingerPrint.category()))
        .mapToDouble(FingerPrint::avgLength).findFirst().orElse(0.0);
    Set<IGoldLabel<String>> factsAsGoldLabels = factsAsGoldLabels(label);
    Set<IGoldLabel<String>> pagesAsGoldLabels = pagesAsGoldLabels(label);
    Multiset<String> boosters = HashMultiset.create();

    factsAsGoldLabels.stream()
        .filter(goldLabel -> goldLabel.isTruePositive() || goldLabel.isFalseNegative())
        .map(IGoldLabel::data)
        .flatMap(fact -> Helpers.features(maxGroupSize, fact).keySet().stream())
        .forEach(boosters::add);

    Set<String> pages =
        pagesAsGoldLabels.stream().map(IGoldLabel::data).collect(Collectors.toSet());

    Set<String> pagesOk = pagesAsGoldLabels.stream()
        .filter(goldLabel -> goldLabel.isTruePositive() || goldLabel.isFalseNegative())
        .map(IGoldLabel::data).collect(Collectors.toSet());

    Set<String> pagesKo = pagesAsGoldLabels.stream()
        .filter(goldLabel -> !goldLabel.isTruePositive() && !goldLabel.isFalseNegative())
        .map(IGoldLabel::data).collect(Collectors.toSet());

    DocSetLabelerImpl docSetLabeler =
        new DocSetLabelerImpl(maxGroupSize, boosters, textCategorizer, (int) avgFingerPrintLength);

    List<Map.Entry<String, Double>> guesstimatedPatterns =
        docSetLabeler.label(Lists.newArrayList(pages), Lists.newArrayList(pagesOk),
            Lists.newArrayList(pagesKo), nbCandidatesToConsider, nbLabelsToReturn);

    List<AbstractLabelingFunction<String>> guesstimatedLabelingFunctions = guesstimatedPatterns
        .stream().map(l -> new MatchRegexLabelingFunction(l.getKey(), true, l.getValue()))
        .collect(Collectors.toList());

    Helpers.serialize(file.getAbsolutePath(), guesstimatedLabelingFunctions);
    return guesstimatedLabelingFunctions;
  }

  public TreeLabelModel<String> labelModel(String label) {

    Preconditions.checkState(isInitialized_, "init should be called first");
    Preconditions.checkNotNull(label, "label should not be null");

    File file = fileTrainedLabelModel(label);

    if (file.exists()) {
      return Helpers.deserialize(file.getAbsolutePath());
    }

    Set<IGoldLabel<String>> goldLabels = pagesAsGoldLabels(label);
    List<Set<IGoldLabel<String>>> devTrainTest = IGoldLabel.split(goldLabels, true, 0.0, 0.75);
    List<IGoldLabel<String>> dev = new ArrayList<>(devTrainTest.get(0));
    List<IGoldLabel<String>> train = new ArrayList<>(devTrainTest.get(1));
    List<IGoldLabel<String>> test = new ArrayList<>(devTrainTest.get(2));

    Preconditions.checkState(train.size() + test.size() == goldLabels.size(),
        "inconsistency found in the number of gold labels in train/test datasets : %s expected vs %s found",
        goldLabels.size(), train.size() + test.size());

    Preconditions.checkState(fileGuesstimatedLabelingFunctions(label).exists(),
        "labelingFunctions must be called first");

    List<AbstractLabelingFunction<String>> labelingFunctions =
        Helpers.deserialize(fileGuesstimatedLabelingFunctions(label).getAbsolutePath());

    TreeLabelModel<String> labelModel =
        new TreeLabelModel<>(labelingFunctions, TreeLabelModel.eMetric.MCC);
    labelModel.fit(train);

    Helpers.serialize(file.getAbsolutePath(), labelModel);
    return labelModel;
  }

  private Set<FactAndDocument> factsAndDocuments(File facts, File documents,
      boolean withProgressBar) {

    File file = fileFactsAndDocuments();

    if (file.exists()) {
      return FactAndDocument.load(file, null, withProgressBar);
    }

    Preconditions.checkState(facts != null, "facts should not be null");
    Preconditions.checkState(facts.exists(), "facts file does not exist : %s", facts);
    Preconditions.checkState(documents != null, "documents should not be null");
    Preconditions.checkState(documents.exists(), "documents file does not exist : %s", documents);

    Set<FactAndDocument> factsAndDocuments =
        FactAndDocument.load(facts, documents, null, withProgressBar);
    FactAndDocument.save(file, factsAndDocuments);
    return factsAndDocuments;
  }

  private Set<IGoldLabel<String>> pagesAsGoldLabels(File facts, File documents,
      boolean withProgressBar) {

    File file = filePagesAsGoldLabels();

    if (file.exists()) {
      return GoldLabelOfString.load(file, null, withProgressBar);
    }

    Set<FactAndDocument> factsAndDocuments = factsAndDocuments(facts, documents, withProgressBar);
    Set<IGoldLabel<String>> goldLabels =
        View.of(FactAndDocument.pagesAsGoldLabels(factsAndDocuments))
            .concat(View.of(FactAndDocument.syntheticGoldLabels(factsAndDocuments))).toSet();

    GoldLabelOfString.save(file, goldLabels);
    return goldLabels;
  }

  private Set<IGoldLabel<String>> factsAsGoldLabels(File facts, File documents,
      boolean withProgressBar) {

    File file = fileFactsAsGoldLabels();

    if (file.exists()) {
      return GoldLabelOfString.load(file, null, withProgressBar);
    }

    Set<FactAndDocument> factsAndDocuments = factsAndDocuments(facts, documents, withProgressBar);
    Set<IGoldLabel<String>> goldLabels = FactAndDocument.factsAsGoldLabels(factsAndDocuments);

    GoldLabelOfString.save(file, goldLabels);
    return goldLabels;
  }

  private File fileFactsAndDocuments() {
    return new File(outputDir_ + File.separator + "facts_and_documents.jsonl.gz");
  }

  private File filePagesAsGoldLabels() {
    return new File(outputDir_ + File.separator + "pages_as_gold_labels.jsonl.gz");
  }

  private File fileFactsAsGoldLabels() {
    return new File(outputDir_ + File.separator + "facts_as_gold_labels.jsonl.gz");
  }

  private File fileTextCategorizer() {
    return new File(outputDir_ + File.separator + "text_categorizer.xml.gz");
  }

  private File fileTextCategorizer(String label) {

    Preconditions.checkNotNull(label, "label should not be null");

    return new File(outputDir_ + File.separator + label + "_text_categorizer.xml.gz");
  }

  private File fileGuesstimatedLabelingFunctions(String label) {

    Preconditions.checkNotNull(label, "label should not be null");

    return new File(outputDir_ + File.separator + label + "_labeling_functions.xml.gz");
  }

  private File fileTrainedLabelModel(String label) {

    Preconditions.checkNotNull(label, "label should not be null");

    return new File(outputDir_ + File.separator + label + "_trained_label_model.xml.gz");
  }

  private String sanitize(String str) {
    return Strings.nullToEmpty(str).replaceAll(SANITIZE_SNIPPET, " ");
  }
}
