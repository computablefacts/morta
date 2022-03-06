package com.computablefacts.morta;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.console.ConsoleApp;
import com.computablefacts.morta.classifiers.AbstractClassifier;
import com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.labelmodels.AbstractLabelModel;
import com.computablefacts.morta.labelmodels.TreeLabelModel;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CheckReturnValue;

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

    Observations observations =
        new Observations(new File(outputDir + File.separator + "observations.txt"));
    Repository repository = new Repository(outputDir, maxGroupSize);
    Set<String> labels = repository.init(facts, documents, verbose).stream()
        .filter(lbl -> label == null || label.equals(lbl)).collect(Collectors.toSet());

    for (String lbl : labels) {
      try {
        observations.add(
            "\n================================================================================");
        observations.add("\nThe label is " + lbl);
        observations.add("\nBuilding alphabet...");

        Dictionary alphabet = repository.alphabet(lbl);

        observations.add("\nThe alphabet size is " + alphabet.size());
        observations.add("\nGuesstimating labeling functions...");
        observations.add("\nThe number of candidates to consider is " + nbCandidatesToConsider);
        observations.add("\nThe number of patterns to return is " + nbLabelsToReturn);

        List<AbstractLabelingFunction<String>> labelingFunctions =
            repository.labelingFunctions(lbl, nbCandidatesToConsider, nbLabelsToReturn);

        observations.add("\nThe returned patterns are : [\n  "
            + Joiner.on(",\n  ").join(labelingFunctions.stream().map(AbstractLabelingFunction::name)
                .collect(Collectors.toList()))
            + "\n]");
        observations.add("\nTraining label model...");
        observations.add("\nThe evaluation metric is MCC");

        AbstractLabelModel<String> labelModel =
            repository.labelModel(lbl, labelingFunctions, TreeLabelModel.eMetric.MCC);

        observations.add("\nThe label model is " + labelModel.toString());
        // observations.add("\nSummarizing labeling functions...");

        // labelModel.summarize(Lists.newArrayList(repository.pagesAsGoldLabels(lbl)))
        // .forEach(summary -> observations.add(String.format("\n%s", summary.toString())));

        observations.add("\nTraining classifier...");
        observations.add("\nThe classifier type is LOGIT");

        AbstractClassifier classifier =
            repository.classifier(lbl, alphabet, labelModel, Repository.eClassifier.LOGIT);
        // TODO : save prodigy annotations

        observations.add("\nComputing label model confusion matrix...");

        List<IGoldLabel<String>> labelModelPredictions = repository.pagesAsGoldLabels(lbl).stream()
            .map(goldLabel -> repository.newGoldLabel(goldLabel,
                labelModel.predict(Lists.newArrayList(goldLabel)).get(0)))
            .collect(Collectors.toList());

        ConfusionMatrix labelModelConfusionMatrix =
            IGoldLabel.confusionMatrix(labelModelPredictions);

        observations.add(labelModelConfusionMatrix.toString());
        observations.add("Computing classifier confusion matrix...");

        List<IGoldLabel<String>> classifierPredictions = repository.pagesAsGoldLabels(lbl).stream()
            .map(goldLabel -> repository.newGoldLabel(goldLabel,
                repository.predict(alphabet, classifier, goldLabel.data())))
            .collect(Collectors.toList());

        ConfusionMatrix classifierConfusionMatrix =
            IGoldLabel.confusionMatrix(classifierPredictions);

        observations.add(classifierConfusionMatrix.toString());

      } catch (Exception e) {
        observations.add(Throwables.getStackTraceAsString(Throwables.getRootCause(e)));
      }
    }

    observations.flush();
  }
}
