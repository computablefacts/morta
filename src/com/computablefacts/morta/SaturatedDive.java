package com.computablefacts.morta;

import static com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction.OK;

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

    Preconditions.checkArgument(nbCandidatesToConsider > 0, "nbCandidatesToConsider must be > 0");
    Preconditions.checkArgument(nbLabelsToReturn > 0, "nbLabelsToReturn must be > 0");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    Repository repository = new Repository(outputDir, maxGroupSize);
    Set<String> labels = repository.init(facts, documents, verbose).stream()
        .filter(lbl -> label == null || label.equals(lbl)).collect(Collectors.toSet());

    for (String lbl : labels) {

      Dictionary alphabet = repository.alphabet(lbl);
      List<AbstractLabelingFunction<String>> labelingFunctions =
          repository.labelingFunctions(lbl, nbCandidatesToConsider, nbLabelsToReturn);
      AbstractLabelModel<String> labelModel =
          repository.labelModel(lbl, labelingFunctions, TreeLabelModel.eMetric.MCC);
      AbstractClassifier classifier =
          repository.classifier(lbl, alphabet, labelModel, Repository.eClassifier.LOGIT);
      // TODO : save prodigy annotations

      System.out.print("\n*** Label Model Summary ***\n");
      System.out.println("Summarizing model : " + labelModel.toString());
      labelModel.summarize(Lists.newArrayList(repository.pagesAsGoldLabels(lbl)))
          .forEach(System.out::println);

      List<IGoldLabel<String>> labelModelPredictions = repository.pagesAsGoldLabels(lbl).stream()
          .map(goldLabel -> newGoldLabel(goldLabel,
              labelModel.predict(Lists.newArrayList(goldLabel)).get(0)))
          .collect(Collectors.toList());

      ConfusionMatrix labelModelConfusionMatrix = IGoldLabel.confusionMatrix(labelModelPredictions);

      System.out.print("\n*** Label Model Confusion Matrix ***");
      System.out.print(labelModelConfusionMatrix);

      List<IGoldLabel<String>> classifierPredictions = repository.pagesAsGoldLabels(lbl).stream()
          .map(goldLabel -> newGoldLabel(goldLabel,
              repository.classify(alphabet, classifier, goldLabel.data())))
          .collect(Collectors.toList());

      ConfusionMatrix classifierConfusionMatrix = IGoldLabel.confusionMatrix(classifierPredictions);

      System.out.print("\n*** Classifier Confusion Matrix ***");
      System.out.print(classifierConfusionMatrix);
    }
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
