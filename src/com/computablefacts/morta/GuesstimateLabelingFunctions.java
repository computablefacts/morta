package com.computablefacts.morta;

import static com.computablefacts.morta.snorkel.ILabelingFunction.KO;
import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.computablefacts.morta.docsetlabeler.DocSetLabelerImpl;
import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.labelmodels.MedianLabelModel;
import com.computablefacts.morta.snorkel.labelingfunctions.MatchWildcardLabelingFunction;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.Languages;
import com.computablefacts.nona.helpers.WildcardMatcher;
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
    boolean dryRun = getBooleanCommand(args, "dry_run", true);
    String outputDirectory = getStringCommand(args, "output_directory", null);

    Preconditions.checkArgument(nbCandidatesToConsider > 0, "nbCandidatesToConsider must be > 0");
    Preconditions.checkArgument(nbLabelsToReturn > 0, "nbLabelsToReturn must be > 0");

    System.out.printf("The label is %s\n", label);
    System.out.printf("The number of candidates to consider (DocSetLabeler) is %d\n",
        nbCandidatesToConsider);
    System.out.printf("The number of labels to return (DocSetLabeler) is %d\n", nbLabelsToReturn);

    // Load gold labels for a given label
    List<IGoldLabel<String>> gls = IGoldLabel.load(goldLabels, label);

    // Pages for which LF must return OK
    System.out.println("Building dataset for label OK...");

    List<String> pagesOk = gls.stream().filter(gl -> MedianLabelModel.label(gl) == OK)
        .map(IGoldLabel::data).collect(Collectors.toList());

    System.out.printf("%d pages found (%d duplicates)\n", pagesOk.size(),
        pagesOk.size() - Sets.newHashSet(pagesOk).size());

    // Pages for which LF must return KO
    System.out.println("Building dataset for label KO...");

    List<String> pagesKo = gls.stream().filter(gl -> MedianLabelModel.label(gl) == KO)
        .map(IGoldLabel::data).collect(Collectors.toList());

    System.out.printf("%d pages found (%d duplicates)\n", pagesKo.size(),
        pagesKo.size() - Sets.newHashSet(pagesKo).size());

    // All pages
    System.out.println("Building the whole dataset...");

    List<String> pages = gls.stream().map(IGoldLabel::data).collect(Collectors.toList());

    System.out.printf("%d pages found (%d duplicates)\n", pages.size(),
        pages.size() - Sets.newHashSet(pages).size());

    // Guesstimate LF
    DocSetLabelerImpl docSetLabeler = new DocSetLabelerImpl(Languages.eLanguage.valueOf(language));

    System.out.println("Starting DocSetLabeler...");

    List<Map.Entry<String, Double>> labels = docSetLabeler.label(pages, pagesOk, pagesKo,
        nbCandidatesToConsider, nbLabelsToReturn, true);

    System.out.println("\nPatterns found : [\n  " + Joiner.on("\n  ").join(labels) + "\n]");

    if (!dryRun) {

      System.out.println("Saving patterns...");

      List<MatchWildcardLabelingFunction> lfs = labels.stream()
          .map(lbl -> new MatchWildcardLabelingFunction(
              WildcardMatcher.compact("*" + lbl.getKey().replace(' ', '*') + "*")))
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
