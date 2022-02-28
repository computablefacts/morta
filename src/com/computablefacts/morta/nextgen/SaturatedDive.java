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
    boolean dryRun = getBooleanCommand(args, "dry_run", true);

    @Var
    GoldLabelsRepository goldLabelsRepository;

    try {
      goldLabelsRepository = new GoldLabelsRepository(outputDir, label);
    } catch (Exception e) {
      goldLabelsRepository = new GoldLabelsRepository(facts, documents, label);
      goldLabelsRepository.save(outputDir, label);
      goldLabelsRepository.export(outputDir, label);
    }

    for (String lbl : goldLabelsRepository.labels()) {

      Optional<TextCategorizer> textCategorizer = goldLabelsRepository.categorizer(lbl);

      if (textCategorizer.isPresent()) {
        System.out.println();
      }
    }

    // 2. Train TextCat on selected snippets
    // 3. Create labeling functions
    // 4. Train generative model
    // 5. Train discriminative model
    // 6. Save model
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
