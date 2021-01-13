package com.computablefacts.morta.poc;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.computablefacts.morta.snorkel.AbstractClassifier;
import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.nona.helpers.AsciiProgressBar;
import com.computablefacts.nona.helpers.Codecs;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.Document;
import com.computablefacts.nona.helpers.Files;
import com.computablefacts.nona.helpers.Languages;
import com.computablefacts.nona.helpers.SnippetExtractor;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.CheckReturnValue;
import com.thoughtworks.xstream.XStream;

@CheckReturnValue
final public class ExecuteModel extends CommandLine {

  private static final char FORM_FEED = '\f';

  public static void main(String[] args) {

    String language = getStringCommand(args, "language", null);
    String label = getStringCommand(args, "label", null);
    File alphabet = getFileCommand(args, "alphabet", null);
    File archive = getFileCommand(args, "archive", null);
    File clazzifier = getFileCommand(args, "classifier", null);
    File labelingFunctions = getFileCommand(args, "labeling_functions", null);
    boolean dryRun = getBooleanCommand(args, "dry_run", true);
    boolean verbose = getBooleanCommand(args, "verbose", false);
    int maxGroupSize = getIntCommand(args, "max_group_size", 4);
    String outputDirectory = getStringCommand(args, "output_directory", null);

    // Load alphabet
    System.out.println("Loading alphabet...");

    XStream xStream = Helpers.xStream();

    Dictionary alpha =
        (Dictionary) xStream.fromXML(Files.compressedLineStream(alphabet, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    System.out.printf("Alphabet size is %d\n", alpha.size());

    // Load classifier
    System.out.println("Loading classifier...");

    AbstractClassifier classifier = (AbstractClassifier) xStream
        .fromXML(Files.compressedLineStream(clazzifier, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    // Load labeling functions
    System.out.println("Loading labeling functions...");

    List<MatchWildcardLabelingFunction> lfs = (List<MatchWildcardLabelingFunction>) xStream
        .fromXML(Files.compressedLineStream(labelingFunctions, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    List<String> keywords =
        lfs.stream().flatMap(lf -> lf.literals().stream()).distinct().collect(Collectors.toList());

    System.out.println("Keywords found : [\n  " + Joiner.on("\n  ").join(keywords) + "\n]");

    // Load documents
    System.out.println("Processing documents...");

    AsciiProgressBar.IndeterminateProgressBar bar = AsciiProgressBar.createIndeterminate();
    Files.compressedLineStream(archive, StandardCharsets.UTF_8)
        .filter(e -> !Strings.isNullOrEmpty(e.getValue()) /* skip empty rows */).peek(e -> {
          if (!verbose) {
            bar.update();
          }
        }).map(e -> {
          try {
            return new Document(Codecs.asObject(e.getValue()));
          } catch (Exception ex) {
            // TODO : logger_.error(Throwables.getStackTraceAsString(Throwables.getRootCause(ex)));
            // TODO : logger_.error("An error occurred on line : \"" + e.getKey() + "\"");
          }
          return null;
        }).filter(doc -> {

          if (doc == null || doc.isEmpty()) {
            return false;
          }

          // Ignore non-pdf files
          if (!"application/pdf".equals(doc.contentType())) {
            return false;
          }
          if (!(doc.text() instanceof String)) {
            return false;
          }
          return true;
        }).forEach(doc -> {

          List<String> pages = Splitter.on(FORM_FEED).splitToList((String) doc.text());

          for (int i = 0; i < pages.size(); i++) {

            String page = pages.get(i);
            FeatureVector<Double> vector =
                Helpers.countVectorizer(Languages.eLanguage.valueOf(language), alpha, maxGroupSize)
                    .apply(page);
            int prediction = classifier.predict(vector);

            if (prediction == MedianLabelModel.LABEL_OK) {

              String snippet = SnippetExtractor.extract(keywords, page);

              if (verbose) {
                System.out.printf("\n%s -> p.%d : %s \n---\n%s\n---", doc.docId(), i + 1, label,
                    snippet.replaceAll("(\r\n|\n)+", "\n"));
              }
            }
          }
        });

    bar.complete();

    System.out.println(); // Cosmetic

    if (!dryRun) {
      // TODO
    }
  }
}
