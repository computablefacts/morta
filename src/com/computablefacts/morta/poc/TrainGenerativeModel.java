package com.computablefacts.morta.poc;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.Summary;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.AsciiTable;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.Files;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.CheckReturnValue;
import com.thoughtworks.xstream.XStream;

import smile.stat.hypothesis.CorTest;

@CheckReturnValue
final public class TrainGenerativeModel extends CommandLine {

  public static void main(String[] args) {

    String language = getStringCommand(args, "language", null);
    String label = getStringCommand(args, "label", null);
    File goldLabels = getFileCommand(args, "gold_labels", null);
    File labelingFunctions = getFileCommand(args, "labeling_functions", null);
    boolean dryRun = getBooleanCommand(args, "dry_run", true);
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

    // Load labeling functions
    System.out.println("Loading labeling functions...");

    XStream xStream = Helpers.xStream();

    List<MatchSequenceLabelingFunction> lfs = (List<MatchSequenceLabelingFunction>) xStream
        .fromXML(Files.compressedLineStream(labelingFunctions, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    // TODO : backport load LF from YAML pattern files

    System.out.printf("%d labeling functions loaded\n", lfs.size());

    // Build label model
    System.out.println("Building label model...");

    MedianLabelModel<String> labelModel = new MedianLabelModel<>(lfs);
    labelModel.fit(train);

    System.out.println(); // Cosmetic

    labelModel.lfSummaries().stream().map(Map.Entry::getValue)
        .sorted((o1, o2) -> Ints.compare(o2.correct(), o1.correct())) // Sort summaries by
                                                                      // decreasing number of
                                                                      // correct labels
        .forEach(summary -> System.out.println("  " + summary.toString()));

    System.out.println("Building correlation matrix for labeling functions...");

    Table<String, String, CorTest> lfCorrelations =
        labelModel.labelingFunctionsCorrelations(train, Summary.eCorrelation.PEARSON);

    System.out.println(); // Cosmetic
    System.out.print(AsciiTable.format(Helpers.correlations(lfCorrelations), true));

    System.out.println("Exploring vectors...");

    System.out.print(AsciiTable.format(Helpers.vectors(labelModel.lfNames(), labelModel.lfLabels(),
        labelModel.vectors(train), labelModel.actual(train), labelModel.predicted(train)), true));

    // Build alphabet (ngrams from 1 included to 6 excluded)
    System.out.println("Building alphabet...");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
    Dictionary alphabet = new Dictionary();

    gls.stream().peek(gl -> bar.update(count.incrementAndGet(), gls.size())).map(IGoldLabel::data)
        .forEach(Helpers.alphabetBuilder(Languages.eLanguage.valueOf(language), alphabet));

    System.out.printf("\nAlphabet size is %d\n", alphabet.size());

    // Compute model accuracy
    System.out.print("Computing confusion matrix for the TRAIN dataset...");
    System.out.println(labelModel.confusionMatrix(train));

    System.out.print("Computing confusion matrix for the TEST dataset...");
    System.out.println(labelModel.confusionMatrix(test));

    System.out.print("Computing confusion matrix for the WHOLE dataset...");
    System.out.println(labelModel.confusionMatrix(gls));

    if (!dryRun) {
      // TODO
    }
  }
}
