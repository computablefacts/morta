package com.computablefacts.morta;

import static com.computablefacts.morta.snorkel.ILabelingFunction.KO;
import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.View;
import com.computablefacts.asterix.console.AsciiProgressBar;
import com.computablefacts.asterix.console.ConsoleApp;
import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.classifiers.*;
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class TrainDiscriminativeModel extends ConsoleApp {

  public static void main(String[] args) {

    String language = getStringCommand(args, "language", null);
    String label = getStringCommand(args, "label", null);
    File goldLabels = getFileCommand(args, "gold_labels", null);
    File labelModel = getFileCommand(args, "label_model", null);
    boolean dryRun = getBooleanCommand(args, "dry_run", true);
    boolean verbose = getBooleanCommand(args, "verbose", false);
    int maxGroupSize = getIntCommand(args, "max_group_size", 1);
    String clazzifier = getStringCommand(args, "classifier", "logit");
    String outputDirectory = getStringCommand(args, "output_directory", null);

    Observations observations = new Observations(new File(Constants.observations(outputDirectory)));
    observations.add(
        "================================================================================\n= Train Discriminative Model\n================================================================================");
    observations.add(String.format("The label is %s", label));
    observations.add(String.format("The language is %s", language));
    observations
        .add(String.format("Max. group size for the 'CountVectorizer' is %d", maxGroupSize));
    observations.add(String.format("The classifier is %s", clazzifier));

    // Load gold labels
    List<IGoldLabel<String>> gls = Helpers.load(observations, goldLabels, label);

    // Split gold labels into train and test
    observations.add("Splitting gold labels into train/test...");

    List<Set<IGoldLabel<String>>> trainTest = IGoldLabel.split(gls, true, 0.0, 0.75);
    List<IGoldLabel<String>> train = new ArrayList<>(trainTest.get(1));
    List<IGoldLabel<String>> test = new ArrayList<>(trainTest.get(2));

    Preconditions.checkState(train.size() + test.size() == gls.size(),
        "Inconsistency found in the number of gold labels : %s expected vs %s found", gls.size(),
        train.size() + test.size());

    observations.add(String.format("Dataset size for training is %d", train.size()));
    observations.add(String.format("Dataset size for testing is %d", test.size()));

    // Build alphabet
    observations.add("Building alphabet...");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
    Map<String, Double> features = new HashMap<>();

    gls.stream().filter(gl -> gl.isTruePositive() || gl.isFalseNegative())
        .peek(gl -> bar.update(count.incrementAndGet(), gls.size())).map(IGoldLabel::data)
        .forEach(text -> Helpers.features(maxGroupSize, text).forEach((f, w) -> {
          if (!features.containsKey(f)) {
            features.put(f, w);
          } else {
            features.put(f, Math.max(features.get(f), w));
          }
        }));

    System.out.println(); // Cosmetic

    observations.add(String.format("Alphabet size is %d", features.size()));
    observations.add("Reducing alphabet...");

    features.entrySet().removeIf(f -> f.getValue() < 0.01);

    Dictionary alphabet = new Dictionary();

    features.forEach((f, w) -> {
      if (!alphabet.containsKey(f)) {
        alphabet.put(f, alphabet.size());
      }
    });

    observations.add(String.format("The new alphabet size is %d", alphabet.size()));

    // Load label model
    observations.add("Loading label model...");

    TreeLabelModel<String> lm = Helpers.deserialize(labelModel.getAbsolutePath());

    // Apply CountVectorizer on gold labels
    observations.add("Applying 'CountVectorizer' on gold labels...");

    count.set(0);
    bar.update(0, train.size());

    List<FeatureVector<Double>> insts =
        View.of(train).peek(gl -> bar.update(count.incrementAndGet(), train.size()))
            .map(IGoldLabel::data).map(Helpers.countVectorizer(alphabet, maxGroupSize)).toList();

    System.out.println(); // Cosmetic

    // Apply label model on gold labels
    observations.add("Applying 'LabelModel' on gold labels...");

    List<Integer> preds = lm.predict(train);

    // Build discriminative model
    observations.add(String.format("Building discriminative model... (%s)", clazzifier));

    AbstractClassifier classifier;

    if ("knn".equals(clazzifier)) {
      classifier = new KNearestNeighborClassifier();
    } else if ("lda".equals(clazzifier)) {
      classifier = new LinearDiscriminantAnalysisClassifier();
    } else if ("fld".equals(clazzifier)) {
      classifier = new FisherLinearDiscriminantClassifier();
    } else if ("qda".equals(clazzifier)) {
      classifier = new QuadraticDiscriminantAnalysisClassifier();
    } else if ("rda".equals(clazzifier)) {
      classifier = new RegularizedDiscriminantAnalysisClassifier();
    } else {
      classifier = new LogisticRegressionClassifier();
    }

    classifier.train(insts, preds);

    // Compute model accuracy
    observations.add("Computing confusion matrix for the TRAIN dataset...");
    observations.add(confusionMatrix(alphabet, maxGroupSize, train, classifier).toString());

    observations.add("Computing confusion matrix for the TEST dataset...");
    observations.add(confusionMatrix(alphabet, maxGroupSize, test, classifier).toString());

    ConfusionMatrix matrix = confusionMatrix(alphabet, maxGroupSize, gls, classifier);
    classifier.mcc(matrix.matthewsCorrelationCoefficient());
    classifier.f1(matrix.f1Score());

    observations.add("Computing confusion matrix for the WHOLE dataset...");
    observations.add(matrix.toString());

    if (!dryRun) {

      observations.add("Saving classifier...");

      Helpers.serialize(Constants.classifierGz(outputDirectory, language, label), classifier);

      observations.add("Saving alphabet...");

      Helpers.serialize(Constants.alphabetGz(outputDirectory, language, label), alphabet);
    }

    observations.flush();
  }

  private static ConfusionMatrix confusionMatrix(Dictionary alphabet, int maxGroupSize,
      List<? extends IGoldLabel<String>> goldLabels, AbstractClassifier classifier) {

    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");
    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkNotNull(classifier, "classifier should not be null");

    List<FeatureVector<Double>> testInsts = View.of(goldLabels).map(IGoldLabel::data)
        .map(Helpers.countVectorizer(alphabet, maxGroupSize)).toList();

    ConfusionMatrix matrix = new ConfusionMatrix();

    matrix.addAll(View.of(goldLabels).map(TreeLabelModel::label).toList(),
        classifier.predict(testInsts), OK, KO);

    return matrix;
  }
}
