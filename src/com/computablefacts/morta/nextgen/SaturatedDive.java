package com.computablefacts.morta.nextgen;

import static com.computablefacts.morta.nextgen.GoldLabelsRepository.ACCEPT;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.console.ConsoleApp;
import com.computablefacts.morta.Observations;
import com.computablefacts.morta.docsetlabeler.DocSetLabelerImpl;
import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.labelingfunctions.MatchRegexLabelingFunction;
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
import com.computablefacts.morta.textcat.FingerPrint;
import com.computablefacts.morta.textcat.TextCategorizer;
import com.computablefacts.morta.yaml.patterns.Pattern;
import com.computablefacts.morta.yaml.patterns.Patterns;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

@CheckReturnValue
final public class SaturatedDive extends ConsoleApp {

  private static final Logger logger_ = LoggerFactory.getLogger(SaturatedDive.class);

  public static void main(String[] args) {

    File facts = getFileCommand(args, "facts", null);
    File documents = getFileCommand(args, "documents", null);
    String outputDir = getStringCommand(args, "output_directory", null);
    String label = getStringCommand(args, "label", null);
    int nbCandidatesToConsider = getIntCommand(args, "nb_candidates_to_consider", 50);
    int nbLabelsToReturn = getIntCommand(args, "nb_labels_to_return", 15);
    int maxGroupSize = getIntCommand(args, "max_group_size", 3);
    String userDefinedLabelingFunctions =
        getStringCommand(args, "user_defined_labeling_functions", null);
    boolean verbose = getBooleanCommand(args, "verbose", true);

    Preconditions.checkArgument(nbCandidatesToConsider > 0, "nbCandidatesToConsider must be > 0");
    Preconditions.checkArgument(nbLabelsToReturn > 0, "nbLabelsToReturn must be > 0");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    // Load gold labels...
    @Var
    GoldLabelsRepository<GoldLabel> goldLabelsRepository;

    try {

      // ...either from existing gold labels...
      goldLabelsRepository = GoldLabelsRepository.fromGoldLabels(outputDir, label, verbose);
    } catch (Exception e2) {

      // ...or from a set of facts and documents
      goldLabelsRepository =
          GoldLabelsRepository.fromFactsAndDocuments(facts, documents, label, verbose);

      // Export gold labels
      goldLabelsRepository.save(outputDir, label);
    }

    // Export Prodigy annotations
    goldLabelsRepository.exportProdigyAnnotations(outputDir, label);

    // Export TextCat configuration
    goldLabelsRepository.exportTextCategories(outputDir, label);

    // Train, test and save model associated with each label
    for (String lbl : goldLabelsRepository.labels()) {

      Observations observations =
          new Observations(new File(outputDir + File.separator + lbl + "_observations.txt"));
      observations.add(String.format("The label is %s", label));
      observations.add(String.format("The output directory is %s", outputDir));
      observations.add(String.format("The maximum ngram length is %d tokens", maxGroupSize));
      observations.add(String.format("The number of candidates to consider (DocSetLabeler) is %d",
          nbCandidatesToConsider));
      observations.add(
          String.format("The number of labels to return (DocSetLabeler) is %d", nbLabelsToReturn));

      goldLabelsRepository.categorizerConfusionMatrix(lbl)
          .ifPresent(matrix -> observations.add(matrix.toString()));

      Optional<TextCategorizer> textCategorizer = goldLabelsRepository.categorizer(lbl);

      if (textCategorizer.isPresent()) {

        // Extract average snippet size
        TextCategorizer categorizer = textCategorizer.get();
        double avgFingerPrintLength = categorizer.categories().stream()
            .filter(fingerPrint -> ACCEPT.equals(fingerPrint.category()))
            .mapToDouble(FingerPrint::avgLength).findFirst().orElse(0.0);

        // Load or guesstimate labeling functions
        List<AbstractLabelingFunction<String>> guesstimatedLabelingFunctions;
        File glfs = new File(outputDir + File.separator + lbl + "_labeling_functions.xml.gz");

        if (glfs.exists()) {
          guesstimatedLabelingFunctions = Helpers.deserialize(glfs.getAbsolutePath());
        } else {

          // Extract patterns found in snippets
          observations.add("Extracting boosters...");

          Multiset<String> boosters = HashMultiset.create();

          goldLabelsRepository.goldLabelsAccepted(label).orElse(new ArrayList<>()).stream()
              .map(IGoldLabel::snippet)
              .flatMap(snippet -> Helpers.features(maxGroupSize, snippet).keySet().stream())
              .forEach(boosters::add);

          observations.add(String.format("%d boosters extracted for label OK (%d uniques)",
              boosters.size(), boosters.elementSet().size()));

          // Build dataset for label OK
          observations.add("Building dataset for label OK...");

          List<String> pagesOk = goldLabelsRepository.goldLabelsAccepted(lbl).map(
              goldLabels -> goldLabels.stream().map(IGoldLabel::data).collect(Collectors.toList()))
              .orElse(new ArrayList<>());

          observations.add(String.format("%d pages found (%d duplicates) in dataset for label OK",
              pagesOk.size(), pagesOk.size() - Sets.newHashSet(pagesOk).size()));

          // Build dataset for label KO
          observations.add("Building dataset for label KO...");

          List<String> pagesKo =
              goldLabelsRepository.goldLabels(lbl)
                  .map(goldLabels -> goldLabels.stream().flatMap(
                      goldLabel -> goldLabel.unmatchedPages().orElse(new ArrayList<>()).stream())
                      .collect(Collectors.toList()))
                  .orElse(new ArrayList<>());

          observations.add(String.format("%d pages found (%d duplicates) in dataset for label KO",
              pagesKo.size(), pagesKo.size() - Sets.newHashSet(pagesKo).size()));

          // Guesstimate labeling functions
          observations.add("Guesstimating labeling functions using DocSetLabeler...");

          DocSetLabelerImpl docSetLabeler = new DocSetLabelerImpl(maxGroupSize, boosters,
              categorizer, (int) avgFingerPrintLength);

          List<Map.Entry<String, Double>> guesstimatedPatterns = docSetLabeler.label(
              Lists.newArrayList(Sets.union(Sets.newHashSet(pagesOk), Sets.newHashSet(pagesKo))),
              Lists.newArrayList(Sets.newHashSet(pagesOk)),
              Lists.newArrayList(Sets.newHashSet(pagesKo)), nbCandidatesToConsider,
              nbLabelsToReturn);

          guesstimatedLabelingFunctions = guesstimatedPatterns.stream()
              .map(l -> new MatchRegexLabelingFunction(l.getKey(), true, l.getValue()))
              .collect(Collectors.toList());

          Helpers.serialize(glfs.getAbsolutePath(), guesstimatedLabelingFunctions);
        }

        observations.add(String.format("Guesstimated patterns : [\n  %s\n]",
            Joiner.on("\n  ").join(guesstimatedLabelingFunctions.stream()
                .map(AbstractLabelingFunction::name).collect(Collectors.toList()))));

        // Load or build label model
        TreeLabelModel<String> labelModel;
        File lm = new File(outputDir + File.separator + lbl + "_label_model.xml.gz");

        if (lm.exists()) {
          labelModel = Helpers.deserialize(lm.getAbsolutePath());
        } else {

          // Split gold labels into train and test
          observations.add("Splitting gold labels into train/test...");

          List<IGoldLabel<String>> goldLabels =
              goldLabelsRepository.goldLabels(lbl).map(gls -> (List) gls).orElse(new ArrayList<>());
          List<Set<IGoldLabel<String>>> trainTest = IGoldLabel.split(goldLabels, true, 0.0, 0.75);
          List<IGoldLabel<String>> train = new ArrayList<>(trainTest.get(1));
          List<IGoldLabel<String>> test = new ArrayList<>(trainTest.get(2));

          Preconditions.checkState(train.size() + test.size() == goldLabels.size(),
              "Inconsistency found in the number of gold labels : %s expected vs %s found",
              goldLabels.size(), train.size() + test.size());

          observations.add(String.format("Dataset size for training is %d", train.size()));
          observations.add(String.format("Dataset size for testing is %d", test.size()));

          // Load user-defined labeling functions
          List<AbstractLabelingFunction<String>> labelingFunctions =
              new ArrayList<>(guesstimatedLabelingFunctions);

          if (!Strings.isNullOrEmpty(userDefinedLabelingFunctions)) {

            File file = new File(userDefinedLabelingFunctions);

            if (file.exists()) {

              Pattern[] patterns = Patterns.load(file, true);

              if (patterns != null) {

                observations.add("Loading user-defined labeling functions...");

                List<AbstractLabelingFunction<String>> udlfs =
                    Patterns.toLabelingFunctions(patterns);
                labelingFunctions.addAll(udlfs);

                observations
                    .add(String.format("%d user-defined labeling functions loaded", udlfs.size()));
              }
            }
          }

          // Build label model
          observations.add("Building label model...");

          labelModel = new TreeLabelModel<>(labelingFunctions);
          labelModel.fit(train);

          observations.add(String.format("Tree for label model is : %s", labelModel));

          labelModel.summarize(train).stream()
              // Sort summaries by decreasing number of correct labels
              .sorted((o1, o2) -> Ints.compare(o2.correct(), o1.correct()))
              .forEach(summary -> observations.add(String.format("  %s", summary)));

          // Compute model accuracy
          observations.add("Computing confusion matrix for the TRAIN dataset...");
          observations.add(labelModel.confusionMatrix(train).toString());

          observations.add("Computing confusion matrix for the TEST dataset...");
          observations.add(labelModel.confusionMatrix(test).toString());

          ConfusionMatrix matrix = labelModel.confusionMatrix(goldLabels);
          labelModel.mcc(matrix.matthewsCorrelationCoefficient());
          labelModel.f1(matrix.f1Score());

          observations.add("Computing confusion matrix for the WHOLE dataset...");
          observations.add(matrix.toString());

          observations.add("Saving label model...");

          Helpers.serialize(lm.getAbsolutePath(), labelModel);

          observations.add("Label model saved.");
        }

        // TODO : Train discriminative model
        // TODO : Save model

        observations.flush();
      }
    }
  }
}
