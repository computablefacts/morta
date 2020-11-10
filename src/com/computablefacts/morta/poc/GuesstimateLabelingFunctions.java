package com.computablefacts.morta.poc;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CheckReturnValue;
import com.thoughtworks.xstream.XStream;

@CheckReturnValue
final public class GuesstimateLabelingFunctions extends CommandLine {

  public static void main(String[] args) {

    String language = getStringCommand(args, "language", null);
    String label = getStringCommand(args, "label", null);
    File goldLabels = getFileCommand(args, "gold_labels", null);
    int nbCandidatesToConsider = getIntCommand(args, "nb_candidates_to_consider", 100);
    int nbLabelsToReturn = getIntCommand(args, "nb_labels_to_return", 50);
    int nbLabelingFunctions = getIntCommand(args, "nb_labeling_functions", 10);
    boolean dryRun = getBooleanCommand(args, "dry_run", true);
    String outputDirectory = getStringCommand(args, "output_directory", null);

    Preconditions.checkArgument(nbCandidatesToConsider > 0, "nbCandidatesToConsider must be > 0");
    Preconditions.checkArgument(nbLabelsToReturn > 0, "nbLabelsToReturn must be > 0");

    System.out.printf("The label is %s\n", label);
    System.out.printf("The number of candidates to consider (DocSetLabeler) is %d\n",
        nbCandidatesToConsider);
    System.out.printf("The number of labels to return (DocSetLabeler) is %d\n", nbLabelsToReturn);
    System.out.printf("The number of Labeling Functions to output is %d\n", nbLabelingFunctions);

    // Load gold labels for a given label
    List<IGoldLabel<String>> gls = IGoldLabel.load(goldLabels, label);

    // Pages for which LF must return LABEL_OK
    System.out.println("Building dataset for LABEL_OK...");

    List<String> pagesOkTmp =
        gls.stream().filter(gl -> MedianLabelModel.label(gl) == MedianLabelModel.LABEL_OK)
            .map(IGoldLabel::data).collect(Collectors.toList());
    Set<String> pagesOk = Sets.newHashSet(pagesOkTmp);

    System.out.printf("%d pages found (%d duplicates)\n", pagesOkTmp.size(),
        pagesOkTmp.size() - pagesOk.size());

    pagesOkTmp.clear(); // Cleanup

    // Pages for which LF must return LABEL_KO
    System.out.println("Building dataset for LABEL_KO...");

    List<String> pagesKoTmp =
        gls.stream().filter(gl -> MedianLabelModel.label(gl) == MedianLabelModel.LABEL_KO)
            .map(IGoldLabel::data).collect(Collectors.toList());
    Set<String> pagesKo = Sets.newHashSet(pagesKoTmp);

    System.out.printf("%d pages found (%d duplicates)\n", pagesKoTmp.size(),
        pagesKoTmp.size() - pagesKo.size());

    pagesKoTmp.clear(); // Cleanup

    // All pages
    System.out.println("Building the whole dataset...");

    List<String> pagesTmp = gls.stream().map(IGoldLabel::data).collect(Collectors.toList());
    Set<String> pages = Sets.newHashSet(pagesTmp);

    System.out.printf("%d pages found (%d duplicates)\n", pagesTmp.size(),
        pagesTmp.size() - pages.size());

    pagesTmp.clear(); // Cleanup

    // Guesstimate LF
    TfIdfDocSetLabeler docSetLabeler =
        new TfIdfDocSetLabeler(Languages.eLanguage.valueOf(language));

    System.out.println("Starting DocSetLabeler...");

    List<Map.Entry<String, Double>> labels = docSetLabeler.label(pages, pagesOk, pagesKo,
        nbCandidatesToConsider, nbLabelsToReturn, true);

    System.out.println("\nPatterns found : [\n  " + Joiner.on("\n  ").join(labels) + "\n]");

    if (!dryRun) {

      System.out.println("Saving patterns...");

      List<MatchSequenceLabelingFunction> lfs = labels.stream()
          .map(lbl -> new MatchSequenceLabelingFunction(Languages.eLanguage.valueOf(language),
              lbl.getKey()))
          .collect(Collectors.toList());

      XStream xStream = Helpers.xStream();

      File input = new File(outputDirectory + File.separator + "labeling_functions_for_" + label
          + "_" + language + ".xml");
      File output = new File(outputDirectory + File.separator + "labeling_functions_for_" + label
          + "_" + language + ".xml.gz");

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(lfs));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);
    }
  }
}