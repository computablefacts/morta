package com.computablefacts.morta;

import static com.computablefacts.morta.Repository.ACCEPT;
import static com.computablefacts.morta.Repository.REJECT;
import static com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction.OK;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.SnippetExtractor;
import com.computablefacts.asterix.View;
import com.computablefacts.asterix.codecs.JsonCodec;
import com.computablefacts.asterix.console.ConsoleApp;
import com.computablefacts.morta.classifiers.AbstractClassifier;
import com.computablefacts.morta.labelingfunctions.AbstractLabelingFunction;
import com.computablefacts.morta.labelmodels.AbstractLabelModel;
import com.computablefacts.morta.labelmodels.TreeLabelModel;
import com.computablefacts.morta.prodigy.AnnotatedText;
import com.computablefacts.morta.prodigy.Meta;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
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
    boolean prodigyDataset = getBooleanCommand(args, "prodigy_dataset", false);
    boolean verbose = getBooleanCommand(args, "verbose", true);

    Preconditions.checkArgument(nbCandidatesToConsider > 0, "nbCandidatesToConsider must be > 0");
    Preconditions.checkArgument(nbLabelsToReturn > 0, "nbLabelsToReturn must be > 0");
    Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

    Observations observations =
        new Observations(new File(outputDir + File.separator + "observations.txt"));
    Repository repository = new Repository(outputDir, maxGroupSize);
    Set<String> labels = repository.init(facts, documents, true, verbose).stream()
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
                labelModel.predict(Lists.newArrayList(goldLabel.data())).get(0)))
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
        observations.add("Exporting prodigy dataset...");

        if (prodigyDataset) {
          exportTextsAsProdigyDataset(repository, lbl, alphabet, classifier, labelingFunctions,
              new File(outputDir + File.separator + lbl + "_prodigy_dataset.jsonl"));
        }

        observations
            .add(String.format("\n%d texts have been exported.", classifierPredictions.size()));

      } catch (Exception e) {
        observations.add(Throwables.getStackTraceAsString(Throwables.getRootCause(e)));
      }
    }

    observations.flush();
  }

  private static void exportTextsAsProdigyDataset(Repository repository, String label,
      Dictionary alphabet, AbstractClassifier classifier,
      List<AbstractLabelingFunction<String>> labelingFunctions, File output) {

    Preconditions.checkNotNull(repository, "repository should not be null");
    Preconditions.checkNotNull(label, "label should not be null");
    Preconditions.checkNotNull(alphabet, "alphabet should not be null");
    Preconditions.checkNotNull(classifier, "classifier should not be null");
    Preconditions.checkNotNull(labelingFunctions, "labelingFunctions should not be null");
    Preconditions.checkNotNull(output, "output should not be null");
    Preconditions.checkArgument(!output.exists(), "output file should not exist : %s", output);

    int maxNumberOfElementsPerClass = 250;
    Set<String> hashAccepted = new HashSet<>();
    Set<String> hashRejected = new HashSet<>();

    View.of(repository.factsAndDocuments(null)).flatten(doc -> {

      // Extract all non-empty pages of all documents
      List<String> pages = doc.unmatchedPages();
      pages.add(doc.matchedPage());
      return View.of(pages).filter(page -> !Strings.isNullOrEmpty(page) /* ignore empty pages */)
          .map(page -> new AbstractMap.SimpleImmutableEntry<>(doc.id(), page));
    }).map(entry -> {

      String docId = entry.getKey();
      String page = entry.getValue();

      // Classify each page and extract a single snippet for each labeling function
      int prediction = repository.predict(alphabet, classifier, page);

      // Extract the keywords associated with the labeling functions
      List<String> keywords = Helpers.keywords(labelingFunctions, page);

      // Extract the snippet associated with the labeling functions
      String snippet =
          keywords.isEmpty() ? "" : SnippetExtractor.extract(keywords, page, 400, 200, "");

      // Create a temporary data structure with all the relevant information
      Annotation annotation = new Annotation();
      annotation.id_ = docId;
      annotation.page_ = page;
      annotation.snippet_ = snippet;
      annotation.labelingFunctions_ = labelingFunctions;
      annotation.keywords_ = keywords;
      annotation.label_ = prediction;

      return annotation;
    }).filter(annotation -> !Strings
        .isNullOrEmpty(annotation.snippet_) /* ignore annotations with empty snippets */)
        .map(annotation -> {

          String matchedKeywords = String.join("/", annotation.keywords_);
          String expectedAnswer = annotation.label_ == OK ? ACCEPT : REJECT;
          Meta meta = new Meta(annotation.id_, label, expectedAnswer, matchedKeywords);

          return new AnnotatedText(meta, annotation.snippet_);
        }).filter(annotatedText -> {

          // Balance the number of ACCEPT/REJECT classes
          if (ACCEPT.equals(annotatedText.meta_.expectedAnswer_)) {
            if (hashAccepted.contains(annotatedText.text_)) {
              return false;
            }
            hashAccepted.add(annotatedText.text_);
            return true;
          }
          if (hashRejected.contains(annotatedText.text_)) {
            return false;
          }
          hashRejected.add(annotatedText.text_);
          return true;
        }).takeWhile(annotatedText -> hashAccepted.size() <= maxNumberOfElementsPerClass
            && hashRejected.size() <= maxNumberOfElementsPerClass)
        .index().peek(entry -> {
          if (entry.getKey() % 10 == 0) {
            System.out.printf("%d annotations exported...%n", entry.getKey());
          }
        }).map(Map.Entry::getValue).toFile(JsonCodec::asString, output, false);
  }

  private final static class Annotation {

    public String id_;
    public String page_;
    public String snippet_;
    public List<AbstractLabelingFunction<String>> labelingFunctions_;
    public List<String> keywords_;
    public int label_;

    public Annotation() {}
  }
}
