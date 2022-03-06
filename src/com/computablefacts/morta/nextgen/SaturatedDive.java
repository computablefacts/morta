package com.computablefacts.morta.nextgen;

import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.console.ConsoleApp;
import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.classifiers.AbstractClassifier;
import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.snorkel.labelmodels.AbstractLabelModel;
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
import com.google.common.base.Preconditions;
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

    Preconditions.checkArgument(label != null, "label should not be null");
    Preconditions.checkArgument(nbCandidatesToConsider > 0, "nbCandidatesToConsider must be > 0");
    Preconditions.checkArgument(nbLabelsToReturn > 0, "nbLabelsToReturn must be > 0");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    Repository repository = new Repository(outputDir, maxGroupSize);
    Set<String> labels = repository.init(facts, documents, verbose);
    Dictionary alphabet = repository.alphabet(label);
    List<AbstractLabelingFunction<String>> labelingFunctions =
        repository.labelingFunctions(label, nbCandidatesToConsider, nbLabelsToReturn);
    AbstractLabelModel<String> labelModel =
        repository.labelModel(label, labelingFunctions, TreeLabelModel.eMetric.MCC);
    AbstractClassifier classifier =
        repository.classifier(label, alphabet, labelModel, Repository.eClassifier.LOGIT);

    List<IGoldLabel<String>> labelModelPredictions =
        repository.pagesAsGoldLabels(label).stream()
            .map(goldLabel -> newGoldLabel(goldLabel,
                labelModel.predict(Lists.newArrayList(goldLabel)).get(0)))
            .collect(Collectors.toList());

    ConfusionMatrix labelModelConfusionMatrix = IGoldLabel.confusionMatrix(labelModelPredictions);

    System.out.print("\n*** Label Model Confusion Matrix ***");
    System.out.print(labelModelConfusionMatrix);

    List<IGoldLabel<String>> classifierPredictions = repository.pagesAsGoldLabels(label).stream()
        .map(goldLabel -> newGoldLabel(goldLabel,
            repository.classify(alphabet, classifier, goldLabel.data())))
        .collect(Collectors.toList());

    ConfusionMatrix classifierConfusionMatrix = IGoldLabel.confusionMatrix(classifierPredictions);

    System.out.print("\n*** Classifier Confusion Matrix ***");
    System.out.print(classifierConfusionMatrix);

    // TODO : save prodigy annotations
  }

  private static GoldLabelOfString newGoldLabel(IGoldLabel<String> goldLabel, int clazz) {
    if (clazz == OK) {
      if (goldLabel.isTruePositive() || goldLabel.isFalseNegative()) {
        return new GoldLabelOfString(goldLabel.id(), goldLabel.label(), goldLabel.data(), false,
            true, false, false);
      }
      return new GoldLabelOfString(goldLabel.id(), goldLabel.label(), goldLabel.data(), false,
          false, false, true);
    }
    if (goldLabel.isTruePositive() || goldLabel.isFalseNegative()) {
      return new GoldLabelOfString(goldLabel.id(), goldLabel.label(), goldLabel.data(), false,
          false, true, false);
    }
    return new GoldLabelOfString(goldLabel.id(), goldLabel.label(), goldLabel.data(), true, false,
        false, false);
  }
}
