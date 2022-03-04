package com.computablefacts.morta.nextgen;

import static com.computablefacts.morta.nextgen.GoldLabelsRepository.ACCEPT;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.asterix.console.ConsoleApp;
import com.computablefacts.morta.docsetlabeler.DocSetLabelerImpl;
import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.labelingfunctions.MatchRegexLabelingFunction;
import com.computablefacts.morta.textcat.FingerPrint;
import com.computablefacts.morta.textcat.TextCategorizer;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
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
    boolean verbose = getBooleanCommand(args, "verbose", true);

    Preconditions.checkArgument(nbCandidatesToConsider > 0, "nbCandidatesToConsider must be > 0");
    Preconditions.checkArgument(nbLabelsToReturn > 0, "nbLabelsToReturn must be > 0");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    if (verbose) {
      System.out.println(String.format("The label is %s", label));
      System.out.println(String.format("The output directory is %s", outputDir));
      System.out.println(String.format("The maximum ngram length is %d tokens", maxGroupSize));
      System.out.println(String.format("The number of candidates to consider (DocSetLabeler) is %d",
          nbCandidatesToConsider));
      System.out.println(
          String.format("The number of labels to return (DocSetLabeler) is %d", nbLabelsToReturn));
    }

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

      if (verbose) {
        goldLabelsRepository.categorizerConfusionMatrix(lbl).ifPresent(System.out::println);
      }

      Optional<TextCategorizer> textCategorizer = goldLabelsRepository.categorizer(lbl);

      if (textCategorizer.isPresent()) {

        // Extract average snippet size
        TextCategorizer categorizer = textCategorizer.get();
        double avgFingerPrintLength = categorizer.categories().stream()
            .filter(fingerPrint -> ACCEPT.equals(fingerPrint.category()))
            .mapToDouble(FingerPrint::avgLength).findFirst().orElse(0.0);

        // Extract patterns found in snippets
        if (verbose) {
          System.out.println("Extracting boosters...");
        }

        Multiset<String> boosters = HashMultiset.create();

        goldLabelsRepository.goldLabelsAccepted(label).orElse(new ArrayList<>()).stream()
            .map(IGoldLabel::snippet)
            .flatMap(snippet -> Helpers.features(maxGroupSize, snippet).keySet().stream())
            .forEach(boosters::add);

        if (verbose) {
          System.out.println(String.format("%d boosters extracted for label OK (%d uniques)",
              boosters.size(), boosters.elementSet().size()));
        }

        // Build dataset for label OK
        if (verbose) {
          System.out.println("Building dataset for label OK...");
        }

        List<String> pagesOk = goldLabelsRepository.goldLabelsAccepted(lbl).map(
            goldLabels -> goldLabels.stream().map(IGoldLabel::data).collect(Collectors.toList()))
            .orElse(new ArrayList<>());

        if (verbose) {
          System.out.println(String.format("%d pages found (%d duplicates) in dataset for label OK",
              pagesOk.size(), pagesOk.size() - Sets.newHashSet(pagesOk).size()));
        }

        // Build dataset for label KO
        if (verbose) {
          System.out.println("Building dataset for label KO...");
        }

        List<String> pagesKo = goldLabelsRepository.goldLabels(lbl)
            .map(goldLabels -> goldLabels.stream()
                .flatMap(goldLabel -> goldLabel.unmatchedPages().orElse(new ArrayList<>()).stream())
                .collect(Collectors.toList()))
            .orElse(new ArrayList<>());

        if (verbose) {
          System.out.println(String.format("%d pages found (%d duplicates) in dataset for label KO",
              pagesKo.size(), pagesKo.size() - Sets.newHashSet(pagesKo).size()));
        }

        // Guesstimate labeling functions
        if (verbose) {
          System.out.println("Guesstimating labeling functions using DocSetLabeler...");
        }

        DocSetLabelerImpl docSetLabeler =
            new DocSetLabelerImpl(maxGroupSize, boosters, categorizer, (int) avgFingerPrintLength);

        List<Map.Entry<String, Double>> guesstimatedPatterns = docSetLabeler.label(
            Lists.newArrayList(Sets.union(Sets.newHashSet(pagesOk), Sets.newHashSet(pagesKo))),
            Lists.newArrayList(Sets.newHashSet(pagesOk)),
            Lists.newArrayList(Sets.newHashSet(pagesKo)), nbCandidatesToConsider, nbLabelsToReturn);

        List<AbstractLabelingFunction<String>> guesstimatedLabelingFunctions = guesstimatedPatterns
            .stream().map(l -> new MatchRegexLabelingFunction(l.getKey(), true, l.getValue()))
            .collect(Collectors.toList());

        File glfs = new File(outputDir + File.separator + lbl + "_labeling_functions.xml.gz");

        if (!glfs.exists()) {
          Helpers.serialize(glfs.getAbsolutePath(), guesstimatedLabelingFunctions);
        }
        if (verbose) {
          System.out.println(String.format("Guesstimated patterns : [\n  %s\n]",
              Joiner.on("\n  ").join(guesstimatedPatterns)));
        }

        // TODO : Train generative model
        // TODO : Train discriminative model
        // TODO : Save model
      }
    }
  }

  private static void trainGenerativeModel() {
    // TODO
  }

  private static void trainDiscriminativeModel() {
    // TODO
  }
}
