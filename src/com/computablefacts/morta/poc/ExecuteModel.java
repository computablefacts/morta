package com.computablefacts.morta.poc;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.computablefacts.junon.Fact;
import com.computablefacts.junon.Metadata;
import com.computablefacts.junon.Provenance;
import com.computablefacts.morta.snorkel.AbstractClassifier;
import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.nona.helpers.Codecs;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.Document;
import com.computablefacts.nona.helpers.Files;
import com.computablefacts.nona.helpers.Languages;
import com.computablefacts.nona.helpers.SnippetExtractor;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
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
    boolean showLogs = getBooleanCommand(args, "show_logs", false);
    int maxGroupSize = getIntCommand(args, "max_group_size", 4);
    String extractedWith = getStringCommand(args, "extracted_with", "morta");
    String extractedBy = getStringCommand(args, "extracted_by", "morta");
    String root = getStringCommand(args, "root", null);
    String dataset = getStringCommand(args, "dataset", null);
    double confidenceScore = getDoubleCommand(args, "confidence_score", 0.5d);
    String output = getStringCommand(args, "output", null);

    Stopwatch stopwatch = Stopwatch.createStarted();

    // Load alphabet
    if (showLogs) {
      System.out.println("Loading alphabet...");
    }

    XStream xStream = Helpers.xStream();

    Dictionary alpha =
        (Dictionary) xStream.fromXML(Files.compressedLineStream(alphabet, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    if (showLogs) {
      System.out.printf("Alphabet size is %d\n", alpha.size());
    }

    // Load classifier
    if (showLogs) {
      System.out.println("Loading classifier...");
    }

    AbstractClassifier classifier = (AbstractClassifier) xStream
        .fromXML(Files.compressedLineStream(clazzifier, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    // Load labeling functions
    if (showLogs) {
      System.out.println("Loading labeling functions...");
    }

    List<MatchWildcardLabelingFunction> lfs = (List<MatchWildcardLabelingFunction>) xStream
        .fromXML(Files.compressedLineStream(labelingFunctions, StandardCharsets.UTF_8)
            .map(Map.Entry::getValue).collect(Collectors.joining("\n")));

    List<String> keywords =
        lfs.stream().flatMap(lf -> lf.literals().stream()).distinct().collect(Collectors.toList());

    if (showLogs) {
      System.out.println("Keywords found : [\n  " + Joiner.on("\n  ").join(keywords) + "\n]");
    }

    // Load documents
    if (showLogs) {
      System.out.println("Processing documents...");
    }

    AtomicInteger nbExtractedFacts = new AtomicInteger(0);
    List<Fact> queue = new ArrayList<>(1000);
    Files.compressedLineStream(archive, StandardCharsets.UTF_8)
        .filter(e -> !Strings.isNullOrEmpty(e.getValue()) /* skip empty rows */).map(e -> {
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

          if (queue.size() >= 1000) {
            if (output == null) {
              queue.stream().map(Codecs::asString).forEach(System.out::println);
            } else if (nbExtractedFacts.get() == queue.size()) {
              Files.create(new File(output),
                  queue.stream().map(Codecs::asString).collect(Collectors.toList()));
            } else {
              Files.append(new File(output),
                  queue.stream().map(Codecs::asString).collect(Collectors.toList()));
            }
            queue.clear();
          }

          List<String> pages = Splitter.on(FORM_FEED).splitToList((String) doc.text());

          for (int i = 0; i < pages.size(); i++) {

            String page = pages.get(i);
            FeatureVector<Double> vector =
                Helpers.countVectorizer(Languages.eLanguage.valueOf(language), alpha, maxGroupSize)
                    .apply(page);
            int prediction = classifier.predict(vector);

            if (prediction == MedianLabelModel.LABEL_OK) {

              String snippet = SnippetExtractor.extract(keywords, page, 300, 50, "");

              if (!Strings.isNullOrEmpty(snippet)) {

                // TODO : cleanup ASAP
                int begin = snippet.indexOf("...") == 0 ? 3 : 0;
                int end = snippet.lastIndexOf("...") == snippet.length() - 3 ? snippet.length() - 3
                    : snippet.length();

                if (0 <= begin && begin <= end) {

                  Fact fact = newFact(extractedWith, extractedBy, root, dataset, doc, label,
                      confidenceScore, i + 1, page, snippet, begin, end);

                  queue.add(fact);
                  nbExtractedFacts.incrementAndGet();

                  if (showLogs) {
                    System.out.printf("\n%s -> p.%d : %s \n---\n%s\n---", doc.docId(), i + 1, label,
                        snippet.replaceAll("(\r\n|\n)+", "\n"));
                  }
                }
              }
            }
          }
        });

    if (queue.size() > 0) {
      if (output == null) {
        queue.stream().map(Codecs::asString).forEach(System.out::println);
      } else if (nbExtractedFacts.get() == queue.size()) {
        Files.create(new File(output),
            queue.stream().map(Codecs::asString).collect(Collectors.toList()));
      } else {
        Files.append(new File(output),
            queue.stream().map(Codecs::asString).collect(Collectors.toList()));
      }
    }

    stopwatch.stop();

    if (showLogs) {
      System.out.println("number of extracted facts : " + nbExtractedFacts.get());
      System.out.println("elapsed time : " + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }
  }

  private static Fact newFact(String extractedWith, String extractedBy, String root, String dataset,
      Document doc, String factType, double confidenceScore, int page, String string, String span,
      int startIndex, int endIndex) {

    Preconditions.checkNotNull(extractedWith, "extractedWith should not be null");
    Preconditions.checkNotNull(extractedBy, "extractedBy should not be null");
    Preconditions.checkNotNull(root, "root should not be null");
    Preconditions.checkNotNull(dataset, "dataset should not be null");
    Preconditions.checkNotNull(doc, "doc should not be null");
    Preconditions.checkNotNull(factType, "factType should not be null");
    Preconditions.checkArgument(0 <= confidenceScore && confidenceScore <= 1.0,
        "confidenceScore must be >= 0 and <= 1");
    Preconditions.checkArgument(page > 0, "page must be > 0");
    Preconditions.checkNotNull(string, "string should not be null");
    Preconditions.checkNotNull(span, "span should not be null");
    Preconditions.checkArgument(0 <= startIndex && startIndex <= span.length(),
        "startIndex must be >= 0 and <= span.length()");
    Preconditions.checkArgument(startIndex <= endIndex && endIndex <= span.length(),
        "endIndex must be >= startIndex and <= span.length()");

    // TODO : legacy code. Remove ASAP.
    String sourceType = "STORAGE/ROOT/DATASET/DOC_ID";
    String sourceStore = "ACCUMULO/" + root + "/" + dataset + "/" + doc.docId();

    Fact fact = new Fact(factType, confidenceScore, null, new Date(), null, true);

    if (dataset.equals("vam")) {
      fact.value(doc.docId());
      fact.value(Integer.toString(page, 10));
      fact.value(Integer.toString(startIndex, 10));
      fact.value(Integer.toString(endIndex, 10));
      fact.value(span);
    } else {

      Object ref0 = doc.metadata().get("ref0");
      Object ref1 = doc.metadata().get("ref1");

      if (ref0 instanceof String && ref1 instanceof String) {
        fact.value((String) ref0);
        fact.value((String) ref1);
        fact.value(Integer.toString(page, 10));
      } else {
        // TODO : process error
      }
    }

    fact.metadata(Lists.newArrayList(new Metadata("Comment", "extracted_with", extractedWith),
        new Metadata("Comment", "extracted_by", extractedBy),
        new Metadata("Comment", "extraction_date", Instant.now().toString())));
    fact.provenance(new Provenance(sourceType, sourceStore, null, null, null, page, null, span,
        startIndex, endIndex));

    return fact;
  }
}
