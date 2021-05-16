package com.computablefacts.morta;

import static com.computablefacts.morta.snorkel.ILabelingFunction.KO;
import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.computablefacts.nona.helpers.Files;
import com.computablefacts.nona.helpers.Languages;
import com.computablefacts.nona.helpers.NGramIterator;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
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

    // Build alphabet
    observations.add("Building alphabet...");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
    @Var
    Dictionary alphabet = new Dictionary();
    Multiset<String> counts = HashMultiset.create();

    gls.stream().peek(gl -> bar.update(count.incrementAndGet(), gls.size())).map(IGoldLabel::data)
        .forEach(
            alphabetBuilder(Languages.eLanguage.valueOf(language), alphabet, counts, maxGroupSize));

    System.out.println(); // Cosmetic

    observations.add(String.format("Alphabet size is %d", alphabet.size()));
    observations.add("Reducing alphabet...");

    alphabet = alphabetReducer(alphabet, counts, gls.size());

    observations.add(String.format("The new alphabet size is %d", alphabet.size()));

    // Load label model
    observations.add("Loading label model...");

    XStream xStream = Helpers.xStream();
    TreeLabelModel<String> lm = (TreeLabelModel<String>) xStream
        .fromXML(Files.loadCompressed(labelModel, StandardCharsets.UTF_8));

    // Apply CountVectorizer on gold labels
    observations.add("Applying 'CountVectorizer' on gold labels...");

    count.set(0);
    bar.update(0, train.size());

    List<FeatureVector<Double>> insts = Pipeline.on(train)
        .peek(gl -> bar.update(count.incrementAndGet(), train.size())).transform(IGoldLabel::data)
        .transform(
            Helpers.countVectorizer(Languages.eLanguage.valueOf(language), alphabet, maxGroupSize))
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
          .add(confusionMatrix(language, alphabet, maxGroupSize, train, classifier).toString());

      observations.add("Computing confusion matrix for the TEST dataset...");
      observations
          .add(confusionMatrix(language, alphabet, maxGroupSize, test, classifier).toString());
    }

    ConfusionMatrix matrix = confusionMatrix(language, alphabet, maxGroupSize, gls, classifier);
    classifier.mcc(matrix.matthewsCorrelationCoefficient());

    if (verbose) {
      observations.add("Computing confusion matrix for the WHOLE dataset...");
      observations.add(matrix.toString());
    }

    if (!dryRun) {

      observations.add("Saving classifier...");

      @Var
      File input = new File(Constants.classifierXml(outputDirectory, language, label));
      @Var
      File output = new File(Constants.classifierGz(outputDirectory, language, label));

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(classifier));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);

      observations.add("Saving alphabet...");

      input = new File(Constants.alphabetXml(outputDirectory, language, label));
      output = new File(Constants.alphabetGz(outputDirectory, language, label));

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(alphabet));
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

    matrix.addAll(Pipeline.on(goldLabels).transform(TreeLabelModel::label).collect(),
        classifier.predict(testInsts), OK, KO);

    return matrix;
  }

  private static Consumer<String> alphabetBuilder(Languages.eLanguage language, Dictionary alphabet,
      Multiset<String> counts, int maxGroupSize) {

    Preconditions.checkNotNull(language, "language should not be null");
    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkNotNull(counts, "counts should not be null");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    return text -> {

      Multiset<String> features = Helpers.features(language, maxGroupSize, text);

      for (int i = 1; i < maxGroupSize; i++) {
        new NGramIterator(i, text, true).forEachRemaining(span -> features.add(span.text()));
      }

      features.entrySet().forEach(ngram -> {

        String word = ngram.getElement();
        int count = ngram.getCount();

        if (!alphabet.containsKey(word)) {
          alphabet.put(word, alphabet.size());
        }
        counts.add(word, count);
      });
    };
  }

  private static Dictionary alphabetReducer(Dictionary alphabet, Multiset<String> counts,
      int nbGoldLabels) {

    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkNotNull(counts, "counts should not be null");
    Preconditions.checkArgument(nbGoldLabels > 0, "nbGoldLabels must be > 0");

    Dictionary newAlphabet = new Dictionary();
    int minDf = (int) (0.01 * nbGoldLabels); // remove outliers
    int maxDf = (int) (0.99 * nbGoldLabels); // remove outliers
    @Var
    int disp = 0;

    for (int i = 0; i < alphabet.size(); i++) {

      String ngram = alphabet.label(i);

      if (counts.count(ngram) < minDf) {
        disp++;
        continue;
      }
      if (counts.count(ngram) > maxDf) {
        disp++;
        continue;
      }
      newAlphabet.put(ngram, i - disp);
    }
    return newAlphabet;
  }
}
