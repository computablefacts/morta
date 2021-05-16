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
import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.labelingfunctions.MatchRegexLabelingFunction;
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
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
    int maxGroupSize = getIntCommand(args, "max_group_size", 4);
    boolean dryRun = getBooleanCommand(args, "dry_run", true);
    String outputDirectory = getStringCommand(args, "output_directory", null);

    Preconditions.checkArgument(nbCandidatesToConsider > 0, "nbCandidatesToConsider must be > 0");
    Preconditions.checkArgument(nbLabelsToReturn > 0, "nbLabelsToReturn must be > 0");

    Observations observations = new Observations(new File(Constants.observations(outputDirectory)));
    observations.add(
        "================================================================================\n= Guesstimate Labeling Functions\n================================================================================");
    observations.add(String.format("The label is %s", label));
    observations.add(String.format("The language is %s", language));
    observations.add(String.format("The maximum ngram length is %d words", maxGroupSize));
    observations.add(String.format("The number of candidates to consider (DocSetLabeler) is %d",
        nbCandidatesToConsider));
    observations.add(
        String.format("The number of labels to return (DocSetLabeler) is %d", nbLabelsToReturn));

    // Load gold labels for a given label
    List<IGoldLabel<String>> gls = IGoldLabel.load(observations, goldLabels, label);

    // Pages for which LF must return OK
    observations.add("Building dataset for label OK...");

    List<String> pagesOk = gls.stream().filter(gl -> TreeLabelModel.label(gl) == OK)
        .map(IGoldLabel::data).collect(Collectors.toList());

    observations.add(String.format("%d pages found (%d duplicates)", pagesOk.size(),
        pagesOk.size() - Sets.newHashSet(pagesOk).size()));

    // Pages for which LF must return KO
    observations.add("Building dataset for label KO...");

    List<String> pagesKo = gls.stream().filter(gl -> TreeLabelModel.label(gl) == KO)
        .map(IGoldLabel::data).collect(Collectors.toList());

    observations.add(String.format("%d pages found (%d duplicates)", pagesKo.size(),
        pagesKo.size() - Sets.newHashSet(pagesKo).size()));

    // All pages
    observations.add("Building the whole dataset...");

    List<String> pages = gls.stream().map(IGoldLabel::data).collect(Collectors.toList());

    observations.add(String.format("%d pages found (%d duplicates)", pages.size(),
        pages.size() - Sets.newHashSet(pages).size()));

    // Guesstimate LF
    DocSetLabelerImpl docSetLabeler =
        new DocSetLabelerImpl(Languages.eLanguage.valueOf(language), maxGroupSize);

    observations.add("Starting DocSetLabeler...");

    List<Map.Entry<String, Double>> labels = docSetLabeler.label(pages, pagesOk, pagesKo,
        nbCandidatesToConsider, nbLabelsToReturn, true);

    observations.add(String.format("Patterns found : [\n  %s\n]", Joiner.on("\n  ").join(labels)));

    if (!dryRun) {

      observations.add("Saving guesstimated labeling functions...");

      List<AbstractLabelingFunction<String>> lfs = labels.stream()
          .map(lbl -> new MatchRegexLabelingFunction(lbl.getKey(), true, lbl.getValue()))
          .collect(Collectors.toList());

      XStream xStream = Helpers.xStream();

      File input =
          new File(Constants.guesstimatedLabelingFunctionsXml(outputDirectory, language, label));
      File output =
          new File(Constants.guesstimatedLabelingFunctionsGz(outputDirectory, language, label));

      com.computablefacts.nona.helpers.Files.create(input, xStream.toXML(lfs));
      com.computablefacts.nona.helpers.Files.gzip(input, output);
      com.computablefacts.nona.helpers.Files.delete(input);
    }

    observations.flush();
  }
}
