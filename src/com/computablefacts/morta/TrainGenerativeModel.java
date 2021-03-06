package com.computablefacts.morta;

import static com.computablefacts.morta.snorkel.ILabelingFunction.KO;
import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.computablefacts.morta.snorkel.GoldLabel;
import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.Summary;
import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.labelmodels.AbstractLabelModel;
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
import com.computablefacts.morta.yaml.patterns.Pattern;
import com.computablefacts.morta.yaml.patterns.Patterns;
import com.computablefacts.nona.helpers.AsciiTable;
import com.computablefacts.nona.helpers.Codecs;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.computablefacts.nona.helpers.Files;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
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
    String outputDirectory = getStringCommand(args, "output_directory", null);

    Observations observations = new Observations(new File(Constants.observations(outputDirectory)));
    observations.add(
        "================================================================================\n= Train Generative Model\n================================================================================");
    observations.add(String.format("The label is %s", label));
    observations.add(String.format("The language is %s", language));

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
        .fromXML(Files.loadCompressed(labelingFunctions, StandardCharsets.UTF_8));

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

    observations.add(String.format("Tree for label model is : %s", labelModel));

    if (verbose) {

      labelModel.lfSummaries().stream()
          // Sort summaries by decreasing number of correct labels
          .sorted((o1, o2) -> Ints.compare(o2.correct(), o1.correct()))
          .forEach(summary -> observations.add(String.format("  %s", summary)));

      observations.add("Building correlation matrix for labeling functions...");

      Table<String, String, CorTest> lfCorrelations =
          labelModel.labelingFunctionsCorrelations(train, Summary.eCorrelation.PEARSON);

      System.out.println(); // Cosmetic
      observations.add(AsciiTable.format(Helpers.correlations(lfCorrelations), true));

      observations.add("Exploring vectors...");
      observations.add(AsciiTable.format(Helpers.vectors(labelModel, train), true));
    }

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

      observations.add("Saving label model...");

      input = new File(Constants.labelModelXml(outputDirectory, language, label));
      output = new File(Constants.labelModelGz(outputDirectory, language, label));

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(labelModel));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);

      observations.add("Saving new gold labels...");

      input = new File(Constants.newGoldLabelsJson(outputDirectory, language, label));
      output = new File(Constants.newGoldLabelsGz(outputDirectory, language, label));

      com.computablefacts.nona.helpers.Files.create(input,
          newGoldLabels(labelModel, gls).stream().map(gl -> {

            Map<String, Object> map = new HashMap<>();
            map.put("id", gl.id());
            map.put("label", gl.label());
            map.put("data", gl.data());
            map.put("snippet", gl.snippet());
            map.put("is_true_positive", gl.isTruePositive());
            map.put("is_false_positive", gl.isFalsePositive());
            map.put("is_true_negative", gl.isTrueNegative());
            map.put("is_false_negative", gl.isFalseNegative());

            return Codecs.asString(map);
          }).collect(Collectors.toList()));

      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);
    }

    observations.flush();
  }

  private static List<IGoldLabel<String>> newGoldLabels(AbstractLabelModel<String> labelModel,
      List<IGoldLabel<String>> gls) {

    Preconditions.checkNotNull(labelModel, "labelModel should not be null");
    Preconditions.checkNotNull(gls, "gls should not be null");

    List<IGoldLabel<String>> goldLabels = new ArrayList<>(gls.size());
    List<Integer> actual = gls.stream().map(TreeLabelModel::label).collect(Collectors.toList());
    List<Integer> predicted = labelModel.predict(gls);

    for (int i = 0; i < gls.size(); i++) {

      int act = actual.get(i);
      int pred = predicted.get(i);

      if (act == pred) {
        goldLabels.add(gls.get(i));
      } else {

        String id = gls.get(i).id();
        String data = gls.get(i).data();
        String label = gls.get(i).label();

        boolean isTruePositive = false;
        boolean isFalsePositive = pred == KO; // the user made a mistake ?
        boolean isTrueNegative = false;
        boolean isFalseNegative = pred == OK; // the user forgot one page ?

        goldLabels.add(new GoldLabel(id, label, data, isTruePositive, isFalsePositive,
            isTrueNegative, isFalseNegative));
      }
    }
    return goldLabels;
  }
}
