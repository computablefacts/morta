package com.computablefacts.morta;

import static com.computablefacts.morta.IGoldLabel.SANITIZE_SNIPPET;
import static com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction.KO;
import static com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction.OK;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.Generated;
import com.computablefacts.asterix.SnippetExtractor;
import com.computablefacts.asterix.View;
import com.computablefacts.morta.bow.BagOfNGrams;
import com.computablefacts.morta.classifiers.*;
import com.computablefacts.morta.docsetlabeler.DocSetLabelerImpl;
import com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.labelingfunctions.MatchRegexLabelingFunction;
import com.computablefacts.morta.labelmodels.AbstractLabelModel;
import com.computablefacts.morta.labelmodels.TreeLabelModel;
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

  public static final String UNKNOWN = "unknown";
  public static final String ACCEPT = "ACCEPT";
  public static final String REJECT = "REJECT";

  private final String outputDir_;
  private final int maxGroupSize_;
  private boolean isInitialized_ = false;

  /**
   * Constructor.
   *
   * @param outputDir    where the temporary files will be written.
   * @param maxGroupSize the maximum number of tokens for a single ngram.
   */
  public Repository(String outputDir, int maxGroupSize) {

    Preconditions.checkNotNull(outputDir, "outputDir should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    outputDir_ = outputDir;
    maxGroupSize_ = maxGroupSize;
  }

  @Generated
  public String directory() {
    return outputDir_;
  }

  @Generated
  public int maxGroupSize() {
    return maxGroupSize_;
  }

  public Optional<TextCategorizer> loadTextCategorizer() {
    File file = fileTextCategorizer();
    if (file.exists()) {
      return Optional.ofNullable(Helpers.deserialize(file.getAbsolutePath()));
    }
    return Optional.empty();
  }

  public Optional<TextCategorizer> loadTextCategorizer(String label) {
    File file = fileTextCategorizer(label);
    if (file.exists()) {
      return Optional.ofNullable(Helpers.deserialize(file.getAbsolutePath()));
    }
    return Optional.empty();
  }

  public Optional<Dictionary> loadAlphabet(String label) {
    File file = fileAlphabet(label);
    if (file.exists()) {
      return Optional.ofNullable(Helpers.deserialize(file.getAbsolutePath()));
    }
    return Optional.empty();
  }

  public Optional<List<AbstractLabelingFunction<String>>> loadLabelingFunctions(String label) {
    File file = fileLabelingFunctions(label);
    if (file.exists()) {
      return Optional.ofNullable(Helpers.deserialize(file.getAbsolutePath()));
    }
    return Optional.empty();
  }

  public Optional<AbstractLabelModel<String>> loadLabelModel(String label) {
    File file = fileLabelModel(label);
    if (file.exists()) {
      return Optional.ofNullable(Helpers.deserialize(file.getAbsolutePath()));
    }
    return Optional.empty();
  }

  public Optional<AbstractClassifier> loadClassifier(String label) {
    File file = fileClassifier(label);
    if (file.exists()) {
      return Optional.ofNullable(Helpers.deserialize(file.getAbsolutePath()));
    }
    return Optional.empty();
  }

  /**
   * Initialize the current repository.
   *
   * @param facts           the facts.
   * @param documents       the facts' underlying documents.
   * @param resize          true iif the fact should be enlarged when less than 300 characters, false
   *                        otherwise.
   * @param withProgressBar true iif a progress bar should be displayed, false otherwise.
   * @return a set of fact types i.e. labels.
   */
  public Set<String> init(File facts, File documents, boolean resize, boolean withProgressBar) {

    Preconditions.checkState(!isInitialized_, "init() should be called only once");

    isInitialized_ = true;

    return View.of(pagesAsGoldLabels(facts, documents, withProgressBar))
        .concat(View.of(factsAsGoldLabels(facts, documents, resize, withProgressBar)))
        .map(IGoldLabel::label).toSet();
  }

  /**
   * Returns all the gold labels found in the current repository.
   *
   * @return the list of gold labels.
   */
  public Set<String> labels() {

    Preconditions.checkState(isInitialized_, "init() should be called first");

    return View.of(pagesAsGoldLabels(null, null, false))
        .concat(View.of(factsAsGoldLabels(null, null, false, false))).map(IGoldLabel::label)
        .toSet();
  }

  /**
   * Load facts and documents for a given label.
   *
   * @param label the label to load. If {@code label} is {@code null}, load all facts and documents.
   * @return a set of facts and documents.
   */
  public Set<FactAndDocument> factsAndDocuments(String label) {

    Preconditions.checkState(isInitialized_, "init() should be called first");

    return FactAndDocument.load(fileFactsAndDocuments(), null, false).stream()
        .filter(goldLabel -> label == null || label.equals(goldLabel.label()))
        .collect(Collectors.toSet());
  }

  /**
   * Load pages as gold labels for a given label.
   *
   * @param label the label to load. If {@code label} is {@code null}, load all pages as gold
   *              labels.
   * @return a set of gold labels.
   */
  public Set<IGoldLabel<String>> pagesAsGoldLabels(String label) {

    Preconditions.checkState(isInitialized_, "init() should be called first");

    return GoldLabelOfString.load(filePagesAsGoldLabels(), null, false).stream()
        .filter(goldLabel -> label == null || label.equals(goldLabel.label()))
        .collect(Collectors.toSet());

  }

  /**
   * Load facts as gold labels for a given label.
   *
   * @param label the label to load. If {@code label} is {@code null}, load all facts as gold
   *              labels.
   * @return a set of gold labels.
   */
  public Set<IGoldLabel<String>> factsAsGoldLabels(String label) {

    Preconditions.checkState(isInitialized_, "init() should be called first");

    return GoldLabelOfString.load(fileFactsAsGoldLabels(), null, false).stream()
        .filter(goldLabel -> label == null || label.equals(goldLabel.label()))
        .collect(Collectors.toSet());
  }

  /**
   * Create a {@link TextCategorizer}.
   * <p>
   * The returned category is the fact type i.e. gold label's label.
   *
   * @return a {@link TextCategorizer}.
   */
  public TextCategorizer textCategorizer() {

    Preconditions.checkState(isInitialized_, "init() should be called first");

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
   * <p>
   * The returned categories are either {@code ACCEPT} or {@code REJECT}.
   *
   * @param label the label to load.
   * @return a {@link TextCategorizer}.
   */
  public TextCategorizer textCategorizer(String label) {

    Preconditions.checkState(isInitialized_, "init() should be called first");
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
   * {@link com.computablefacts.morta.docsetlabeler.DocSetLabelerImpl} algorithm.
   *
   * @param label                  the label for which the labeling functions must be guesstimated.
   * @param nbCandidatesToConsider number of candidate labels the
   *                               {@link com.computablefacts.morta.docsetlabeler.DocSetLabelerImpl} algorithm should consider
   *                               on each iteration.
   * @param nbLabelsToReturn       number of labels the
   *                               {@link com.computablefacts.morta.docsetlabeler.DocSetLabelerImpl} algorithm should return at
   *                               the end.
   * @return a list of labeling functions.
   */
  public List<AbstractLabelingFunction<String>> labelingFunctions(String label,
      int nbCandidatesToConsider, int nbLabelsToReturn) {

    Preconditions.checkState(isInitialized_, "init() should be called first");
    Preconditions.checkNotNull(label, "label should not be null");
    Preconditions.checkArgument(nbCandidatesToConsider > 0, "nbCandidatesToConsider must be > 0");
    Preconditions.checkArgument(nbLabelsToReturn > 0, "nbLabelsToReturn must be > 0");

    File file = fileLabelingFunctions(label);

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
        .flatMap(fact -> Helpers.features(maxGroupSize_, fact).keySet().stream())
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
        new DocSetLabelerImpl(maxGroupSize_, boosters, textCategorizer, (int) avgFingerPrintLength);

    List<Map.Entry<String, Double>> guesstimatedPatterns =
        docSetLabeler.label(Lists.newArrayList(pages), Lists.newArrayList(pagesOk),
            Lists.newArrayList(pagesKo), nbCandidatesToConsider, nbLabelsToReturn);

    List<AbstractLabelingFunction<String>> guesstimatedLabelingFunctions = guesstimatedPatterns
        .stream().map(l -> new MatchRegexLabelingFunction(l.getKey(), true, l.getValue()))
        .collect(Collectors.toList());

    Helpers.serialize(file.getAbsolutePath(), guesstimatedLabelingFunctions);
    return guesstimatedLabelingFunctions;
  }

  /**
   * Load or train a label model.
   *
   * @param label             the label for which a label model must be trained.
   * @param labelingFunctions the set of labeling functions to use.
   * @param metric            the metric to use to evaluate the trained model.
   * @return a label model.
   */
  public AbstractLabelModel<String> labelModel(String label,
      List<AbstractLabelingFunction<String>> labelingFunctions, TreeLabelModel.eMetric metric) {

    Preconditions.checkState(isInitialized_, "init() should be called first");
    Preconditions.checkNotNull(label, "label should not be null");
    Preconditions.checkNotNull(labelingFunctions, "labelingFunctions should not be null");
    Preconditions.checkNotNull(metric, "metric should not be null");

    File file = fileLabelModel(label);

    if (file.exists()) {
      return Helpers.deserialize(file.getAbsolutePath());
    }

    Set<IGoldLabel<String>> goldLabels = pagesAsGoldLabels(label);
    List<Set<IGoldLabel<String>>> devTrainTest = IGoldLabel.split(goldLabels, true, 0.0, 0.75);
    List<IGoldLabel<String>> train = new ArrayList<>(devTrainTest.get(1));
    List<IGoldLabel<String>> test = new ArrayList<>(devTrainTest.get(2));

    Preconditions.checkState(train.size() + test.size() == goldLabels.size(),
        "inconsistency found in the number of gold labels in train/test datasets : %s expected vs %s found",
        goldLabels.size(), train.size() + test.size());

    TreeLabelModel<String> labelModel = new TreeLabelModel<>(labelingFunctions, metric);
    labelModel.fit(train);

    List<IGoldLabel<String>> predictions = test.stream()
        .map(goldLabel -> newGoldLabel(goldLabel,
            labelModel.predict(Lists.newArrayList(goldLabel.data())).get(0)))
        .collect(Collectors.toList());

    ConfusionMatrix confusionMatrix = IGoldLabel.confusionMatrix(predictions);

    labelModel.f1(confusionMatrix.f1Score());
    labelModel.mcc(confusionMatrix.matthewsCorrelationCoefficient());

    Helpers.serialize(file.getAbsolutePath(), labelModel);
    return labelModel;
  }

  /**
   * Load or train a classifier.
   *
   * @param label      the label for which a classifier must be trained.
   * @param alphabet   the alphabet to use.
   * @param labelModel the label model to use.
   * @param clazzifier the classifier to use.
   * @return a classifier.
   */
  public AbstractClassifier classifier(String label, Dictionary alphabet,
      AbstractLabelModel<String> labelModel, eClassifier clazzifier) {

    Preconditions.checkState(isInitialized_, "init() should be called first");
    Preconditions.checkNotNull(label, "label should not be null");
    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkNotNull(labelModel, "labelModel should not be null");
    Preconditions.checkNotNull(clazzifier, "clazzifier should not be null");

    File file = fileClassifier(label);

    if (file.exists()) {
      return Helpers.deserialize(file.getAbsolutePath());
    }

    Set<IGoldLabel<String>> goldLabels = pagesAsGoldLabels(label);
    List<Set<IGoldLabel<String>>> devTrainTest = IGoldLabel.split(goldLabels, true, 0.0, 0.75);
    List<IGoldLabel<String>> train = new ArrayList<>(devTrainTest.get(1));
    List<IGoldLabel<String>> test = new ArrayList<>(devTrainTest.get(2));

    Preconditions.checkState(train.size() + test.size() == goldLabels.size(),
        "inconsistency found in the number of gold labels in train/test datasets : %s expected vs %s found",
        goldLabels.size(), train.size() + test.size());

    List<FeatureVector<Double>> actuals = train.stream().map(IGoldLabel::data)
        .map(countVectorizer(alphabet, maxGroupSize_)).collect(Collectors.toList());

    List<Integer> predictions =
        labelModel.predict(train.stream().map(IGoldLabel::data).collect(Collectors.toList()));

    AbstractClassifier classifier;

    if (eClassifier.KNN.equals(clazzifier)) {
      classifier = new KNearestNeighborClassifier();
    } else if (eClassifier.LDA.equals(clazzifier)) {
      classifier = new LinearDiscriminantAnalysisClassifier();
    } else if (eClassifier.FLD.equals(clazzifier)) {
      classifier = new FisherLinearDiscriminantClassifier();
    } else if (eClassifier.QDA.equals(clazzifier)) {
      classifier = new QuadraticDiscriminantAnalysisClassifier();
    } else if (eClassifier.RDA.equals(clazzifier)) {
      classifier = new RegularizedDiscriminantAnalysisClassifier();
    } else {
      classifier = new LogisticRegressionClassifier();
    }

    classifier.train(actuals, predictions);

    List<IGoldLabel<String>> newPredictions = test.stream()
        .map(goldLabel -> newGoldLabel(goldLabel, predict(alphabet, classifier, goldLabel.data())))
        .collect(Collectors.toList());

    ConfusionMatrix confusionMatrix = IGoldLabel.confusionMatrix(newPredictions);

    classifier.f1(confusionMatrix.f1Score());
    classifier.mcc(confusionMatrix.matthewsCorrelationCoefficient());

    Helpers.serialize(file.getAbsolutePath(), classifier);
    return classifier;
  }

  /**
   * Load or compute the alphabet associated with a set of verified gold labels.
   *
   * @param label the label for which the alphabet must be computed.
   * @return the alphabet.
   */
  public Dictionary alphabet(String label) {

    Preconditions.checkState(isInitialized_, "init() should be called first");
    Preconditions.checkNotNull(label, "label should not be null");

    File file = fileAlphabet(label);

    if (file.exists()) {
      return Helpers.deserialize(file.getAbsolutePath());
    }

    Dictionary alphabet = new Dictionary();
    Map<String, Double> features = new HashMap<>();

    // Load accepted gold labels and extract features
    pagesAsGoldLabels(label).stream()
        .filter(goldLabel -> goldLabel.isTruePositive() || goldLabel.isFalseNegative())
        .map(IGoldLabel::data)
        .forEach(text -> Helpers.features(maxGroupSize_, text).forEach((feature, weight) -> {
          if (!features.containsKey(feature)) {
            features.put(feature, weight);
          } else {
            features.put(feature, Math.max(features.get(feature), weight));
          }
        }));

    // Remove low cardinality features
    features.entrySet().removeIf(feature -> feature.getValue() < 0.01);

    // Build the alphabet from the feature set
    features.forEach((feature, weight) -> {
      if (!alphabet.containsKey(feature)) {
        alphabet.put(feature, alphabet.size());
      }
    });

    Helpers.serialize(file.getAbsolutePath(), alphabet);
    return alphabet;
  }

  /**
   * Classify a given text.
   *
   * @param labelModel the label model to use.
   * @param text       the text to classify.
   * @return a label in {OK, KO}.
   */
  public int predict(AbstractLabelModel<String> labelModel, String text) {

    Preconditions.checkNotNull(labelModel, "labelModel should not be null");
    Preconditions.checkNotNull(text, "text should not be null");

    return labelModel.predict(Lists.newArrayList(text)).get(0);
  }

  /**
   * Classify a given text.
   *
   * @param alphabet   the alphabet to use.
   * @param classifier the classifier to use.
   * @param text       the text to classify.
   * @return a label in {OK, KO}.
   */
  public int predict(Dictionary alphabet, AbstractClassifier classifier, String text) {

    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkNotNull(classifier, "classifier should not be null");
    Preconditions.checkNotNull(text, "text should not be null");

    return classifier.predict(countVectorizer(alphabet, maxGroupSize_).apply(text));
  }

  /**
   * On positive classification, returns a snippet of text centered around its most 'interesting'
   * part.
   *
   * @param labelModel        the label model to use.
   * @param labelingFunctions the labeling functions to use.
   * @param text              the text to classify.
   * @return a snippet centered around its most 'interesting' part (if any).
   */
  public Optional<String> predictAndGetFocusPoint(AbstractLabelModel<String> labelModel,
      List<AbstractLabelingFunction<String>> labelingFunctions, String text) {

    Preconditions.checkNotNull(labelModel, "labelModel should not be null");
    Preconditions.checkNotNull(labelingFunctions, "labelingFunctions should not be null");
    Preconditions.checkNotNull(text, "text should not be null");

    int prediction = predict(labelModel, text);

    if (prediction != OK) {
      return Optional.empty();
    }

    List<String> keywords = Helpers.keywords(labelingFunctions, text);

    if (keywords.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(SnippetExtractor.extract(keywords, text, 300, 50, ""));
  }

  /**
   * On positive classification, returns a snippet of text centered around its most 'interesting'
   * part.
   *
   * @param alphabet          the alphabet to use.
   * @param classifier        the classifier to use.
   * @param labelingFunctions the labeling functions to use.
   * @param text              the text to classify.
   * @return a snippet centered around its most 'interesting' part (if any).
   */
  public Optional<String> predictAndGetFocusPoint(Dictionary alphabet,
      AbstractClassifier classifier, List<AbstractLabelingFunction<String>> labelingFunctions,
      String text) {

    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkNotNull(classifier, "classifier should not be null");
    Preconditions.checkNotNull(labelingFunctions, "labelingFunctions should not be null");
    Preconditions.checkNotNull(text, "text should not be null");

    int prediction = predict(alphabet, classifier, text);

    if (prediction != OK) {
      return Optional.empty();
    }

    List<String> keywords = Helpers.keywords(labelingFunctions, text);

    if (keywords.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(SnippetExtractor.extract(keywords, text, 300, 50, ""));
  }

  /**
   * Create a new gold label by comparing the gold label actual class with the predicted one.
   *
   * @param goldLabel  the actual gold label.
   * @param prediction the prediction.
   * @return a new gold label.
   */
  public GoldLabelOfString newGoldLabel(IGoldLabel<String> goldLabel, int prediction) {

    Preconditions.checkNotNull(goldLabel, "goldLabel should not be null");
    Preconditions.checkArgument(prediction == OK || prediction == KO,
        "the prediction should be in {OK, KO}");

    if (prediction == OK) {
      if (goldLabel.isTruePositive() || goldLabel.isFalseNegative()) {
        return new GoldLabelOfString(goldLabel.id(), goldLabel.label(), goldLabel.data(), false,
            true, false, false);
      }
      return new GoldLabelOfString(goldLabel.id(), goldLabel.label(), goldLabel.data(), false,
          false, false, true);
    }
    if (goldLabel.isTruePositive() || goldLabel.isFalseNegative()) {
      return new GoldLabelOfString(goldLabel.id(), goldLabel.label(), goldLabel.data(), false,
          false, true, false);
    }
    return new GoldLabelOfString(goldLabel.id(), goldLabel.label(), goldLabel.data(), true, false,
        false, false);
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

  private Set<IGoldLabel<String>> factsAsGoldLabels(File facts, File documents, boolean resize,
      boolean withProgressBar) {

    File file = fileFactsAsGoldLabels();

    if (file.exists()) {
      return GoldLabelOfString.load(file, null, withProgressBar);
    }

    Set<FactAndDocument> factsAndDocuments = factsAndDocuments(facts, documents, withProgressBar);
    Set<IGoldLabel<String>> goldLabels =
        FactAndDocument.factsAsGoldLabels(factsAndDocuments, resize);

    GoldLabelOfString.save(file, goldLabels);
    return goldLabels;
  }

  private Function<String, FeatureVector<Double>> countVectorizer(Dictionary alphabet,
      int maxGroupSize) {

    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    return text -> {

      FeatureVector<Double> vector = new FeatureVector<>(alphabet.size(), 0.0);
      Map<String, Double> features = Helpers.features(maxGroupSize, text);

      features.forEach((f, w) -> {
        if (alphabet.containsKey(f)) {
          vector.set(alphabet.id(f), 1.0);
        }
      });
      return vector;
    };
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

  private File fileLabelingFunctions(String label) {

    Preconditions.checkNotNull(label, "label should not be null");

    return new File(outputDir_ + File.separator + label + "_labeling_functions.xml.gz");
  }

  private File fileLabelModel(String label) {

    Preconditions.checkNotNull(label, "label should not be null");

    return new File(outputDir_ + File.separator + label + "_label_model.xml.gz");
  }

  private File fileClassifier(String label) {

    Preconditions.checkNotNull(label, "label should not be null");

    return new File(outputDir_ + File.separator + label + "_classifier.xml.gz");
  }

  private File fileAlphabet(String label) {

    Preconditions.checkNotNull(label, "label should not be null");

    return new File(outputDir_ + File.separator + label + "_alphabet.xml.gz");
  }

  private String sanitize(String str) {
    return Strings.nullToEmpty(str).replaceAll(SANITIZE_SNIPPET, " ");
  }

  public enum eClassifier {
    KNN, LDA, FLD, QDA, RDA, LOGIT
  }
}
