package com.computablefacts.morta.nextgen;

import java.io.File;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.asterix.console.ConsoleApp;
import com.computablefacts.morta.textcat.TextCategorizer;
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
    boolean verbose = getBooleanCommand(args, "verbose", true);

    // Load gold labels...
    @Var
    GoldLabelsRepository goldLabelsRepository;

    try {

      // ...from existing annotations...
      goldLabelsRepository = GoldLabelsRepository.fromProdigyAnnotations(outputDir, label, verbose);
    } catch (Exception e1) {
      try {

        // ...or from existing gold labels...
        goldLabelsRepository = GoldLabelsRepository.fromGoldLabels(outputDir, label, verbose);
      } catch (Exception e2) {

        // ...or from a set of facts and documents
        goldLabelsRepository =
            GoldLabelsRepository.fromFactsAndDocuments(facts, documents, label, verbose);
        goldLabelsRepository.save(outputDir, label);
      }

      // Export gold labels as Prodigy annotations
      goldLabelsRepository.export(outputDir, label);
    }

    // Process labels:
    // - Create labeling functions
    // - Train generative model
    // - Train discriminative model
    // - Save final model
    for (String lbl : goldLabelsRepository.labels()) {

      if (verbose) {
        goldLabelsRepository.categorizerConfusionMatrix(lbl).ifPresent(System.out::println);
      }

      Optional<TextCategorizer> textCategorizer = goldLabelsRepository.categorizer(lbl);

      if (textCategorizer.isPresent()) {

        System.out.println();

        // Create labeling functions
        // Train generative model
        // Train discriminative model
        // Save model
      }
    }
  }

  private static void trainTextCat() {
    // TODO
  }

  private static void craftLabelingFunctions() {
    // TODO
  }

  private static void trainGenerativeModel() {
    // TODO
  }

  private static void trainDiscriminativeModel() {
    // TODO
  }
}
