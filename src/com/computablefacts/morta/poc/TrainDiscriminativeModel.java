package com.computablefacts.morta.poc;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.computablefacts.morta.Pipeline;
import com.computablefacts.morta.snorkel.AbstractClassifier;
import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.morta.snorkel.FisherLinearDiscriminantClassifier;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.KNearestNeighborClassifier;
import com.computablefacts.morta.snorkel.LinearDiscriminantAnalysisClassifier;
import com.computablefacts.morta.snorkel.LogisticRegressionClassifier;
import com.computablefacts.morta.snorkel.QuadraticDiscriminantAnalysisClassifier;
import com.computablefacts.morta.snorkel.RegularizedDiscriminantAnalysisClassifier;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.computablefacts.nona.helpers.Files;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;
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

    // Load gold labels
    List<IGoldLabel<String>> gls = IGoldLabel.load(goldLabels, label);

    // Split gold labels into train and test
    System.out.println("Splitting gold labels into train/test...");

    List<Set<IGoldLabel<String>>> trainTest = IGoldLabel.split(gls, true, 0.0, 0.75);
    List<IGoldLabel<String>> train = new ArrayList<>(trainTest.get(1));
    List<IGoldLabel<String>> test = new ArrayList<>(trainTest.get(2));

    Preconditions.checkState(train.size() + test.size() == gls.size(),
        "Inconsistency found in the number of gold labels : %s expected vs %s found", gls.size(),
        train.size() + test.size());

    System.out.printf("Dataset size for training is %d\n", train.size());
    System.out.printf("Dataset size for testing is %d\n", test.size());

    // Load alphabet
    System.out.println("Loading alphabet...");

    XStream xStream = Helpers.xStream();

    Dictionary alpha =
        (Dictionary) xStream.fromXML(Files.compressedLineStream(alphabet, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    System.out.printf("Alphabet size is %d\n", alpha.size());

    // Load label model
    System.out.println("Loading label model...");

    MedianLabelModel<String> lm = (MedianLabelModel<String>) xStream
        .fromXML(Files.compressedLineStream(labelModel, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    // Apply CountVectorizer on gold labels
    System.out.println("Applying 'CountVectorizer' on gold labels...");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();

    List<FeatureVector<Double>> insts = Pipeline.on(train)
        .peek(gl -> bar.update(count.incrementAndGet(), train.size())).transform(IGoldLabel::data)
        .transform(
            Helpers.countVectorizer(Languages.eLanguage.valueOf(language), alpha, maxGroupSize))
        .collect();

    System.out.println(); // Cosmetic

    // Apply label model on gold labels
    System.out.println("Applying 'LabelModel' on gold labels...");

    List<Integer> preds = lm.predict(train);

    // Build discriminative model
    System.out.printf("Building discriminative model... (%s)\n", clazzifier);

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

      System.out.print("Computing confusion matrix for the TRAIN dataset...");
      System.out.println(confusionMatrix(language, alpha, maxGroupSize, train, classifier));

      System.out.print("Computing confusion matrix for the TEST dataset...");
      System.out.println(confusionMatrix(language, alpha, maxGroupSize, test, classifier));

      System.out.print("Computing confusion matrix for the WHOLE dataset...");
      System.out.println(confusionMatrix(language, alpha, maxGroupSize, gls, classifier));
    }

    if (!dryRun) {

      System.out.println("Saving classifier...");

      @Var
      File input = new File(
          outputDirectory + File.separator + "classifier_for_" + label + "_" + language + ".xml");
      @Var
      File output = new File(outputDirectory + File.separator + "classifier_for_" + label + "_"
          + language + ".xml.gz");

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(classifier));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);
    }
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
        classifier.predict(testInsts), MedianLabelModel.LABEL_OK, MedianLabelModel.LABEL_KO);

    return matrix;
  }
}
