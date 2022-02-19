package com.computablefacts.morta;

import static com.computablefacts.morta.snorkel.ILabelingFunction.KO;
import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.knallgrau.utils.textcat.FingerPrint;
import org.knallgrau.utils.textcat.TextCategorizer;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.View;
import com.computablefacts.asterix.codecs.JsonCodec;
import com.computablefacts.asterix.console.ConsoleApp;
import com.computablefacts.morta.docsetlabeler.DocSetLabelerImpl;
import com.computablefacts.morta.snorkel.GoldLabel;
import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.labelingfunctions.MatchRegexLabelingFunction;
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
import com.computablefacts.nona.helpers.Languages;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class GuesstimateLabelingFunctions extends ConsoleApp {

  public static void main(String[] args) {

    String language = getStringCommand(args, "language", null);
    String label = getStringCommand(args, "label", null);
    File goldLabels = getFileCommand(args, "gold_labels", null);
    int nbCandidatesToConsider = getIntCommand(args, "nb_candidates_to_consider", 100);
    int nbLabelsToReturn = getIntCommand(args, "nb_labels_to_return", 50);
    int maxGroupSize = getIntCommand(args, "max_group_size", 1);
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

    List<String> pagesOk = gls.stream().filter(gl -> !Strings.isNullOrEmpty(gl.data()))
        .filter(gl -> TreeLabelModel.label(gl) == OK).map(IGoldLabel::data)
        .collect(Collectors.toList());

    observations.add(String.format("%d pages found (%d duplicates)", pagesOk.size(),
        pagesOk.size() - Sets.newHashSet(pagesOk).size()));

    List<String> snippets = gls.stream().filter(gl -> !Strings.isNullOrEmpty(gl.snippet()))
        .filter(gl -> TreeLabelModel.label(gl) == OK).map(IGoldLabel::snippet)
        .collect(Collectors.toList());

    observations.add(String.format("%d snippets extracted for label OK", snippets.size()));

    // Extract patterns found in snippets
    Multiset<String> boosters = HashMultiset.create();

    snippets
        .stream().flatMap(s -> Helpers
            .features(Languages.eLanguage.valueOf(language), maxGroupSize, s).keySet().stream())
        .forEach(boosters::add);

    observations.add(String.format("%d boosters extracted for label OK (%d uniques)",
        boosters.size(), boosters.elementSet().size()));

    // Pages for which LF must return KO
    observations.add("Building dataset for label KO...");

    List<String> pagesKo = gls.stream().filter(gl -> !Strings.isNullOrEmpty(gl.data()))
        .filter(gl -> TreeLabelModel.label(gl) == KO).map(IGoldLabel::data)
        .collect(Collectors.toList());

    observations.add(String.format("%d pages found (%d duplicates)", pagesKo.size(),
        pagesKo.size() - Sets.newHashSet(pagesKo).size()));

    // All pages
    observations.add("Building the whole dataset...");

    List<String> pages = gls.stream().filter(gl -> !Strings.isNullOrEmpty(gl.data()))
        .map(IGoldLabel::data).collect(Collectors.toList());

    observations.add(String.format("%d pages found (%d duplicates)", pages.size(),
        pages.size() - Sets.newHashSet(pages).size()));

    // Compute a positive and negative 'TextCat' LF
    observations.add("Computing positive/negative fingerprints...");

    StringBuilder ok = new StringBuilder();
    StringBuilder ko = new StringBuilder();

    double avgLength = gls.stream().filter(gl -> !Strings.isNullOrEmpty(gl.snippet()))
        .filter(gl -> label.equals(gl.label())).peek(gl -> {
          if (TreeLabelModel.label(gl) == OK) {
            ok.append(gl.snippet().replaceAll("\\s+", " ")).append("\n\n\n");
          } else {
            ko.append(gl.snippet().replaceAll("\\s+", " ")).append("\n\n\n");
          }
        }).map(gl -> gl.snippet().length()).mapToInt(i -> i).average().orElse(0);

    observations.add(String.format("Average snippet length is %f", avgLength));

    FingerPrint fpOk = new FingerPrint();
    fpOk.setCategory("OK");
    fpOk.create(ok.toString());

    FingerPrint fpKo = new FingerPrint();
    fpKo.setCategory("KO");
    fpKo.create(ko.toString());

    double weight = testFingerprints(observations, goldLabels, label, fpOk, fpKo);

    TextCategorizer categorizer = new TextCategorizer();
    categorizer.add(fpOk);
    categorizer.add(fpKo);

    // Guesstimate LF
    DocSetLabelerImpl docSetLabeler = new DocSetLabelerImpl(Languages.eLanguage.valueOf(language),
        maxGroupSize, boosters, categorizer, (int) avgLength);

    observations.add("Starting DocSetLabeler...");

    List<Map.Entry<String, Double>> labels =
        docSetLabeler.label(pages, pagesOk, pagesKo, nbCandidatesToConsider, nbLabelsToReturn);

    observations.add(String.format("Patterns found : [\n  %s\n]", Joiner.on("\n  ").join(labels)));

    if (!dryRun) {

      observations.add("Saving guesstimated labeling functions...");

      List<AbstractLabelingFunction<String>> lfs = labels.stream()
          .map(lbl -> new MatchRegexLabelingFunction(lbl.getKey(), true, lbl.getValue()))
          .collect(Collectors.toList());

      Helpers.serialize(Constants.guesstimatedLabelingFunctionsGz(outputDirectory, language, label),
          lfs);
    }

    observations.flush();
  }

  private static double testFingerprints(Observations observations, File goldLabels, String label,
      FingerPrint ok, FingerPrint ko) {

    Preconditions.checkNotNull(observations, "observations should not be null");
    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkNotNull(label, "label should not be null");
    Preconditions.checkNotNull(ok, "ok should not be null");
    Preconditions.checkNotNull(ko, "ko should not be null");

    TextCategorizer guesser = new TextCategorizer();
    guesser.add(ok);
    guesser.add(ko);

    observations.add("Computing TextCat confusion matrix...");

    ConfusionMatrix matrix = new ConfusionMatrix();

    int nbUnknownCategories =
        View.of(goldLabels, true).index().filter(e -> !Strings.isNullOrEmpty(e.getValue()))
            .map(e -> (IGoldLabel<String>) new GoldLabel(JsonCodec.asObject(e.getValue())))
            .filter(gl -> !Strings.isNullOrEmpty(gl.snippet()))
            .filter(gl -> label.equals(gl.label())).map(gl -> {

              String category = guesser.categorize(gl.snippet().replaceAll("\\s+", " "));

              if ("unknown".equals(category)) {
                observations.add("We encountered an unknown TextCat category -> " + gl.snippet());
                return 1;
              }
              if (TreeLabelModel.label(gl) == OK && category.endsWith("OK")) {
                matrix.addTruePositives(1);
              } else if (TreeLabelModel.label(gl) == KO && category.endsWith("KO")) {
                matrix.addTrueNegatives(1);
              } else if (TreeLabelModel.label(gl) == OK && category.endsWith("KO")) {
                matrix.addFalseNegatives(1);
              } else {
                matrix.addFalsePositives(1);
              }
              return 0;
            }).reduce(0, Integer::sum);

    observations.add(String.format("The number of unknown categories is %d", nbUnknownCategories));
    observations.add(matrix.toString());

    return matrix.f1Score();
  }
}
