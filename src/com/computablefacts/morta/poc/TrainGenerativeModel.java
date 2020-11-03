package com.computablefacts.morta.poc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.computablefacts.morta.snorkel.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.Summary;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.ConfusionMatrix;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.Var;
import com.thoughtworks.xstream.XStream;

final public class TrainGenerativeModel extends CommandLine {

  public static void main(String[] args) {

    String language = getStringCommand(args, "language", null);
    String label = getStringCommand(args, "label", null);
    File goldLabels = getFileCommand(args, "gold_labels", null);
    int nbCandidatesToConsider = getIntCommand(args, "nb_candidates_to_consider", 100);
    int nbLabelsToReturn = getIntCommand(args, "nb_labels_to_return", 50);
    int nbLabelingFunctions = getIntCommand(args, "nb_labeling_functions", 10);
    boolean dryRun = getBooleanCommand(args, "dry_run", true);
    String outputDirectory = getStringCommand(args, "output_directory", null);

    System.out.printf("The number of candidates to consider is %d (DocSetLabeler)\n",
        nbCandidatesToConsider);
    System.out.printf("The number of labels to return is %d (DocSetLabeler)\n", nbLabelsToReturn);
    System.out.printf("The number of LF to keep is %d\n", nbLabelingFunctions);

    // Load gold labels
    List<IGoldLabel<String>> gls = IGoldLabel.load(goldLabels);

    // Split gold labels into train and test
    System.out.println("Splitting gold labels into train/test...");

    List<Set<IGoldLabel<String>>> trainTest = IGoldLabel.split(gls, true, 0.0, 0.75);
    List<IGoldLabel<String>> train = new ArrayList<>(trainTest.get(1));
    List<IGoldLabel<String>> test = new ArrayList<>(trainTest.get(2));

    Preconditions.checkState(train.size() + test.size() == gls.size(),
        "Inconsistency found in the number of gold labels : %s expected vs %s found", gls.size(),
        train.size() + test.size());

    System.out.printf("Dataset size for training is %d\n", train.size());
    System.out.printf("Dataset size for testing is %d", test.size());

    // Guesstimate LF using the train dataset
    TfIdfDocSetLabeler docSetLabeler =
        new TfIdfDocSetLabeler(Languages.eLanguage.valueOf(language));

    List<Map.Entry<String, Double>> labels =
        labels(train, docSetLabeler, nbCandidatesToConsider, nbLabelsToReturn);

    List<AbstractLabelingFunction<String>> lfs = labels.stream()
        .map(lbl -> new MatchPatternLabelingFunction(Languages.eLanguage.valueOf(language),
            lbl.getKey()))
        .collect(Collectors.toList());

    // TODO : backport load LF from file

    // LF Analysis
    System.out.println("Initializing label model...");

    // TODO : backport LF randomization
    MedianLabelModel<String> labelModel = new MedianLabelModel<>(lfs, train);

    List<Summary> summaries = labelModel.summarize();

    // Sort summaries by decreasing number of correct labels
    summaries.sort((o1, o2) -> Ints.compare(o2.correct(), o1.correct()));
    summaries.forEach(summary -> System.out.println("  " + summary.toString()));

    // Building alphabet (ngrams from 1 included to 6 excluded)
    System.out.println("Building alphabet...");

    AtomicInteger count = new AtomicInteger(0);
    AsciiProgressBar.ProgressBar bar = AsciiProgressBar.create();
    Function<String, List<String>> sentenceSplitter = Helpers.sentenceSplitter();
    Function<String, List<String>> wordSplitter =
        Helpers.wordSplitter(Languages.eLanguage.valueOf(language));

    Dictionary dico = new Dictionary();

    gls.stream()/* .filter(gl -> gl.isTruePositive() || gl.isFalseNegative()) */.forEach(gl -> {

      bar.update(count.incrementAndGet(), gls.size());

      List<List<String>> sentences = sentenceSplitter.apply(gl.data()).stream()
          .map(sentence -> wordSplitter.apply(sentence)).collect(Collectors.toList());

      for (int i = 0; i < sentences.size(); i++) {

        List<String> sentence = sentences.get(i);

        for (int j = 0; j < sentence.size(); j++) {
          for (int l = j + 1; l < sentence.size() && l < j + 6; l++) {

            String ngram = Joiner.on(' ').join(sentence.subList(j, l));

            if (!dico.containsKey(ngram)) {
              dico.put(ngram, dico.size());
            }
          }
        }
      }
    });

    // Model Accuracy
    System.out.print("\nComputing confusion matrix for the TRAIN dataset...");

    MedianLabelModel<String> trainLabelModel = new MedianLabelModel<>(lfs, train,
        labelModel.lfSummaries(), labelModel.thresholdOk(), labelModel.thresholdKo());
    ConfusionMatrix trainMatrix = trainLabelModel.confusionMatrix();

    System.out.println(trainMatrix.toString());
    System.out.print("Computing confusion matrix for the TEST dataset...");

    MedianLabelModel<String> testLabelModel = new MedianLabelModel<>(lfs, test,
        labelModel.lfSummaries(), labelModel.thresholdOk(), labelModel.thresholdKo());
    ConfusionMatrix testMatrix = testLabelModel.confusionMatrix();

    System.out.println(testMatrix.toString());
    System.out.print("Computing confusion matrix for the WHOLE dataset...");

    MedianLabelModel<String> wholeLabelModel = new MedianLabelModel<>(lfs,
        Lists.newArrayList(Sets.union(Sets.newHashSet(train), Sets.newHashSet(test))),
        labelModel.lfSummaries(), labelModel.thresholdOk(), labelModel.thresholdKo());
    ConfusionMatrix matrix = wholeLabelModel.confusionMatrix();

    System.out.println(matrix.toString());

    // Backup Model
    if (!dryRun) {

      System.out.println("\nWriting output...");

      XStream xStream = Helpers.xStream();

      @Var
      File input = new File(outputDirectory + "\\generative_model_for_" + label + ".xml");
      @Var
      File output = new File(outputDirectory + "\\generative_model_for_" + label + ".xml.gz");

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(labelModel));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);

      input = new File(outputDirectory + "\\generative_model_alphabet_for_" + label + ".xml");
      output = new File(outputDirectory + "\\generative_model_alphabet_for_" + label + ".xml.gz");

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(dico));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);
    }
  }

  private static List<Map.Entry<String, Double>> labels(List<IGoldLabel<String>> goldLabels,
      TfIdfDocSetLabeler docSetLabeler, int nbCandidatesToConsider, int nbLabelsToReturn) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkNotNull(docSetLabeler, "docSetLabeler should not be null");
    Preconditions.checkArgument(nbCandidatesToConsider > 0, "nbCandidatesToConsider must be > 0");
    Preconditions.checkArgument(nbLabelsToReturn > 0, "nbLabelsToReturn must be > 0");

    // Pages for which LFs must return CLASS_OK
    System.out.println("\nBuilding dataset for LABEL_OK...");

    List<String> classOkTmp =
        goldLabels.stream().filter(gl -> gl.isTruePositive() || gl.isFalseNegative())
            .map(IGoldLabel::data).collect(Collectors.toList());
    Set<String> classOk = Sets.newHashSet(classOkTmp);

    System.out.printf("%d pages found (%d duplicates)", classOkTmp.size(),
        classOkTmp.size() - classOk.size());

    classOkTmp.clear(); // Cleanup

    // Pages for which LFs must return LABEL_KO
    System.out.println("\nBuilding dataset for LABEL_KO...");

    List<String> classKoTmp =
        goldLabels.stream().filter(gl -> gl.isFalsePositive() || gl.isTrueNegative())
            .map(IGoldLabel::data).collect(Collectors.toList());
    Set<String> classKo = Sets.newHashSet(classKoTmp);

    System.out.printf("%d pages found (%d duplicates)", classKoTmp.size(),
        classKoTmp.size() - classKo.size());

    classKoTmp.clear(); // Cleanup

    // All pages from all documents
    System.out.println("\nBuilding the whole dataset...");

    List<String> pagesTmp = goldLabels.stream().map(IGoldLabel::data).collect(Collectors.toList());
    Set<String> pages = Sets.newHashSet(pagesTmp);

    System.out.printf("%d pages found (%d duplicates)", pagesTmp.size(),
        pagesTmp.size() - pages.size());

    pagesTmp.clear(); // Cleanup

    // Guesstimate labeling functions
    System.out.println("\nStarting DocSetLabeler...");

    List<Map.Entry<String, Double>> labels = docSetLabeler.label(pages, classOk, classKo,
        nbCandidatesToConsider, nbLabelsToReturn, true);

    System.out.println("\nPatterns found : [\n  " + Joiner.on("\n  ").join(labels) + "\n]");

    return labels;
  }
}
