package com.computablefacts.morta;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.Summary;
import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
import com.computablefacts.morta.yaml.patterns.Pattern;
import com.computablefacts.morta.yaml.patterns.Patterns;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.AsciiTable;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.computablefacts.nona.helpers.Files;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;
import com.thoughtworks.xstream.XStream;

import smile.stat.hypothesis.CorTest;

@CheckReturnValue
final public class TrainGenerativeModel extends CommandLine {

  public static void main(String[] args) {

    String language = getStringCommand(args, "language", null);
    String label = getStringCommand(args, "label", null);
    File goldLabels = getFileCommand(args, "gold_labels", null);
    File labelingFunctions = getFileCommand(args, "labeling_functions", null);
    String userDefinedLabelingFunctions =
        getStringCommand(args, "user_defined_labeling_functions", null);
    boolean dryRun = getBooleanCommand(args, "dry_run", true);
    boolean verbose = getBooleanCommand(args, "verbose", false);
    int maxGroupSize = getIntCommand(args, "max_group_size", 4);
    String outputDirectory = getStringCommand(args, "output_directory", null);

    Observations observations = new Observations(new File(Constants.observations(outputDirectory)));
    observations.add(
        "================================================================================\n= Train Generative Model\n================================================================================");
    observations.add(String.format("The label is %s", label));
    observations.add(String.format("The language is %s", language));
    observations
        .add(String.format("Max. group size for the 'CountVectorizer' is %d", maxGroupSize));

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

    // Load guesstimated labeling functions
    observations.add("Loading labeling functions...");

    XStream xStream = Helpers.xStream();

    List<AbstractLabelingFunction<String>> lfs = (List<AbstractLabelingFunction<String>>) xStream
        .fromXML(Files.compressedLineStream(labelingFunctions, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    observations.add(String.format("%d labeling functions loaded", lfs.size()));

    // Load user-defined labeling functions
    if (!Strings.isNullOrEmpty(userDefinedLabelingFunctions)) {

      File file = new File(userDefinedLabelingFunctions);

      if (file.exists()) {

        Pattern[] patterns = Patterns.load(file, true);

        if (patterns != null) {

          observations.add("Loading user-defined labeling functions...");

          List<AbstractLabelingFunction<String>> udlfs = Patterns.toLabelingFunctions(patterns);
          lfs.addAll(udlfs);

          observations.add(String.format("%d labeling functions loaded", udlfs.size()));
        }
      }
    }

    observations.add(String.format("Patterns found : [\n  %s\n]", Joiner.on("\n  ")
        .join(lfs.stream().map(AbstractLabelingFunction::name).collect(Collectors.toList()))));

    // Build label model
    observations.add("Building label model...");

    TreeLabelModel<String> labelModel = new TreeLabelModel<>(lfs);
    labelModel.fit(train);

    System.out.println(); // Cosmetic

    observations.add(String.format("Tree for label model is : %s", labelModel.toString()));

    if (verbose) {

      labelModel.lfSummaries().stream()
          // Sort summaries by decreasing number of correct labels
          .sorted((o1, o2) -> Ints.compare(o2.correct(), o1.correct()))
          .forEach(summary -> observations.add(String.format("  %s", summary.toString())));

      observations.add("Building correlation matrix for labeling functions...");

      Table<String, String, CorTest> lfCorrelations =
          labelModel.labelingFunctionsCorrelations(train, Summary.eCorrelation.PEARSON);

      System.out.println(); // Cosmetic
      observations.add(AsciiTable.format(Helpers.correlations(lfCorrelations), true));

      observations.add("Exploring vectors...");
      observations.add(AsciiTable.format(Helpers.vectors(labelModel, train), true));
    }

    // Build alphabet (ngrams from 1 included to 6 excluded)
    observations.add("Building alphabet...");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
    @Var
    Dictionary alphabet = new Dictionary();
    Multiset<String> counts = HashMultiset.create();

    gls.stream().peek(gl -> bar.update(count.incrementAndGet(), gls.size())).map(IGoldLabel::data)
        .forEach(Helpers.alphabetBuilder(Languages.eLanguage.valueOf(language), alphabet, counts,
            maxGroupSize));

    System.out.println(); // Cosmetic
    observations.add(String.format("Alphabet size is %d", alphabet.size()));
    observations.add("Reducing alphabet...");

    alphabet = Helpers.alphabetReducer(alphabet, counts, gls.size());

    observations.add(String.format("The new alphabet size is %d", alphabet.size()));

    // Compute model accuracy
    if (verbose) {

      observations.add("Computing confusion matrix for the TRAIN dataset...");
      observations.add(labelModel.confusionMatrix(train).toString());

      observations.add("Computing confusion matrix for the TEST dataset...");
      observations.add(labelModel.confusionMatrix(test).toString());
    }

    ConfusionMatrix matrix = labelModel.confusionMatrix(gls);
    labelModel.mcc(matrix.matthewsCorrelationCoefficient());

    if (verbose) {
      observations.add("Computing confusion matrix for the WHOLE dataset...");
      observations.add(matrix.toString());
    }

    if (!dryRun) {

      observations.add("Saving labeling functions...");

      @Var
      File input = new File(Constants.labelingFunctionsXml(outputDirectory, language, label));
      @Var
      File output = new File(Constants.labelingFunctionsGz(outputDirectory, language, label));

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(lfs));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);

      observations.add("Saving alphabet...");

      input = new File(Constants.alphabetXml(outputDirectory, language, label));
      output = new File(Constants.alphabetGz(outputDirectory, language, label));

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(alphabet));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);

      observations.add("Saving counts...");

      input = new File(Constants.countsXml(outputDirectory, language, label));
      output = new File(Constants.countsGz(outputDirectory, language, label));

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(counts));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);

      observations.add("Saving label model...");

      input = new File(Constants.labelModelXml(outputDirectory, language, label));
      output = new File(Constants.labelModelGz(outputDirectory, language, label));

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(labelModel));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);
    }

    observations.flush();
  }
}
