package com.computablefacts.morta;

import static com.computablefacts.morta.snorkel.ILabelingFunction.KO;
import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.Pipeline;
import com.computablefacts.morta.snorkel.classifiers.AbstractClassifier;
import com.computablefacts.morta.snorkel.classifiers.FisherLinearDiscriminantClassifier;
import com.computablefacts.morta.snorkel.classifiers.KNearestNeighborClassifier;
import com.computablefacts.morta.snorkel.classifiers.LinearDiscriminantAnalysisClassifier;
import com.computablefacts.morta.snorkel.classifiers.LogisticRegressionClassifier;
import com.computablefacts.morta.snorkel.classifiers.QuadraticDiscriminantAnalysisClassifier;
import com.computablefacts.morta.snorkel.classifiers.RegularizedDiscriminantAnalysisClassifier;
import com.computablefacts.morta.snorkel.labelmodels.MedianLabelModel;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.computablefacts.nona.helpers.Files;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;
import com.thoughtworks.xstream.XStream;

@CheckReturnValue
final public class TrainDiscriminativeModel extends CommandLine {

  public static void main(String[] args) {

    String language = getStringCommand(args, "language", null);
    String label = getStringCommand(args, "label", null);
    File goldLabels = getFileCommand(args, "gold_labels", null);
    File labelModel = getFileCommand(args, "label_model", null);
    File alphabet = getFileCommand(args, "alphabet", null);
    boolean dryRun = getBooleanCommand(args, "dry_run", true);
    boolean verbose = getBooleanCommand(args, "verbose", false);
    int maxGroupSize = getIntCommand(args, "max_group_size", 4);
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
    List<IGoldLabel<String>> gls = IGoldLabel.load(observations, goldLabels, label);

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

    // Load alphabet
    observations.add("Loading alphabet...");

    XStream xStream = Helpers.xStream();

    Dictionary alpha =
        (Dictionary) xStream.fromXML(Files.compressedLineStream(alphabet, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    observations.add(String.format("Alphabet size is %d", alpha.size()));

    // Load label model
    observations.add("Loading label model...");

    MedianLabelModel<String> lm = (MedianLabelModel<String>) xStream
        .fromXML(Files.compressedLineStream(labelModel, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    // Apply CountVectorizer on gold labels
    observations.add("Applying 'CountVectorizer' on gold labels...");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

    List<FeatureVector<Double>> insts = Pipeline.on(train)
        .peek(gl -> bar.update(count.incrementAndGet(), train.size())).transform(IGoldLabel::data)
        .transform(
            Helpers.countVectorizer(Languages.eLanguage.valueOf(language), alpha, maxGroupSize))
        .collect();

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
    if (verbose) {

      observations.add("Computing confusion matrix for the TRAIN dataset...");
      observations
          .add(confusionMatrix(language, alpha, maxGroupSize, train, classifier).toString());

      observations.add("Computing confusion matrix for the TEST dataset...");
      observations.add(confusionMatrix(language, alpha, maxGroupSize, test, classifier).toString());
    }

    ConfusionMatrix matrix = confusionMatrix(language, alpha, maxGroupSize, gls, classifier);
    classifier.mcc(matrix.matthewsCorrelationCoefficient());

    if (verbose) {
      observations.add("Computing confusion matrix for the WHOLE dataset...");
      observations.add(matrix.toString());
    }

    if (!dryRun) {

      observations.add("Saving classifier...");

      File input = new File(Constants.classifierXml(outputDirectory, language, label));
      File output = new File(Constants.classifierGz(outputDirectory, language, label));

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(classifier));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);
    }

    observations.flush();
  }

  private static ConfusionMatrix confusionMatrix(String language, Dictionary alphabet,
      int maxGroupSize, List<? extends IGoldLabel<String>> goldLabels,
      AbstractClassifier classifier) {

    Preconditions.checkNotNull(language, "language should not be null");
    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");
    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkNotNull(classifier, "classifier should not be null");

    List<FeatureVector<Double>> testInsts = Pipeline.on(goldLabels).transform(IGoldLabel::data)
        .transform(
            Helpers.countVectorizer(Languages.eLanguage.valueOf(language), alphabet, maxGroupSize))
        .collect();

    ConfusionMatrix matrix = new ConfusionMatrix();

    matrix.addAll(Pipeline.on(goldLabels).transform(MedianLabelModel::label).collect(),
        classifier.predict(testInsts), OK, KO);

    return matrix;
  }
}
