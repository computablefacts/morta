package com.computablefacts.morta;

import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.junon.Fact;
import com.computablefacts.junon.Metadata;
import com.computablefacts.junon.Provenance;
import com.computablefacts.nona.helpers.Codecs;
import com.computablefacts.nona.helpers.CommandLine;
import com.computablefacts.nona.helpers.Document;
import com.computablefacts.nona.helpers.Files;
import com.computablefacts.nona.helpers.SnippetExtractor;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class ExecuteModel extends CommandLine {

  private static final Logger logger_ = LoggerFactory.getLogger(ExecuteModel.class);
  private static final char FORM_FEED = '\f';

  public static void main(String[] args) {

    String language = getStringCommand(args, "language", null);
    String label = getStringCommand(args, "label", null);
    File archive = getFileCommand(args, "archive", null);
    int maxGroupSize = getIntCommand(args, "max_group_size", 4);
    String extractedWith = getStringCommand(args, "extracted_with", "morta");
    String extractedBy = getStringCommand(args, "extracted_by", "morta");
    String root = getStringCommand(args, "root", null);
    String dataset = getStringCommand(args, "dataset", null);
    String input = getStringCommand(args, "input", null);
    String output = getStringCommand(args, "output", null);
    double threshold = getDoubleCommand(args, "threshold", 0.7);
    String outputDirectory = getStringCommand(args, "output_directory", null);

    Observations observations = new Observations(new File(Constants.observations(outputDirectory)));
    observations.add(
        "================================================================================\n= Execute Model\n================================================================================");
    observations.add(String.format("The label is %s", label));
    observations.add(String.format("The language is %s", language));
    observations.add(String.format("The root is %s", root));
    observations.add(String.format("The dataset is %s", dataset));
    observations.add(String.format("The threshold is %f", threshold));
    observations
        .add(String.format("Max. group size for the 'CountVectorizer' is %d", maxGroupSize));

    // Loading model
    observations.add("Loading model...");

    Model model = new Model(language, label, maxGroupSize);

    if (!model.init(input)) {
      observations.add("Extraction failed : invalid model");
    } else if (model.confidenceScore() < threshold) {
      observations.add(
          String.format("Extraction failed : confidence score is less than threshold (%f < %f)",
              model.confidenceScore(), threshold));
    } else {

      observations.add(String.format("Alphabet size is %d", model.alphabet().size()));
      observations.add(String.format("Model confidence score is %f", model.confidenceScore()));
      observations.add(
          String.format("Keywords found : [\n  %s\n]", Joiner.on("\n  ").join(model.keywords())));
      observations.add("Processing documents...");

      AtomicInteger nbExtractedFacts = new AtomicInteger(0);
      List<Fact> queue = new ArrayList<>(1000);
      List<Model> models = Lists.newArrayList(model);

      Files.compressedLineStream(archive, StandardCharsets.UTF_8)
          .filter(e -> !Strings.isNullOrEmpty(e.getValue()) /* skip empty rows */).map(e -> {
            try {
              return new Document(Codecs.asObject(e.getValue()));
            } catch (Exception ex) {
              logger_.error(Throwables.getStackTraceAsString(Throwables.getRootCause(ex)));
              logger_.error(String.format("An error occurred on line : \"%s\"", e.getKey()));
            }
            return null;
          }).filter(Objects::nonNull).forEach(doc -> {

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

            List<Fact> facts =
                apply(observations, extractedWith, extractedBy, root, dataset, models, doc);

            if (!facts.isEmpty()) {
              queue.addAll(facts);
              nbExtractedFacts.addAndGet(facts.size());
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

      observations.add(String.format("Number of extracted facts : %d", nbExtractedFacts.get()));
    }

    observations.flush();
  }

  public static List<Fact> apply(String extractedWith, String extractedBy, String root,
      String dataset, List<Model> models, Document doc) {
    return apply(null, extractedWith, extractedBy, root, dataset, models, doc);
  }

  private static List<Fact> apply(Observations observations, String extractedWith,
      String extractedBy, String root, String dataset, List<Model> models, Document doc) {

    Preconditions.checkNotNull(extractedWith, "extractedWith should not be null");
    Preconditions.checkNotNull(extractedBy, "extractedBy should not be null");
    Preconditions.checkNotNull(root, "root should not be null");
    Preconditions.checkNotNull(dataset, "dataset should not be null");
    Preconditions.checkNotNull(doc, "doc should not be null");

    List<Fact> facts = new ArrayList<>();

    if (doc.isEmpty()) {
      return facts;
    }
    if (!"application/pdf".equals(doc.contentType())) { // Ignore non-pdf files
      return facts;
    }
    if (!(doc.text() instanceof String)) {
      return facts;
    }

    List<String> pages = Splitter.on(FORM_FEED).splitToList((String) doc.text());

    for (int i = 0; i < pages.size(); i++) {

      int pageIndex = i;
      String page = pages.get(pageIndex);

      models.stream().filter(Model::isValid).forEach(model -> {

        int prediction = model.classifier().predict(model.countVectorizer().apply(page));

        if (prediction == OK) {

          String snippet = SnippetExtractor.extract(model.keywords(), page, 300, 50, "");

          if (!Strings.isNullOrEmpty(snippet)) {

            Fact fact = newFact(extractedWith, extractedBy, root, dataset, doc, model.name(),
                model.confidenceScore(), pageIndex + 1, page, snippet, 0, snippet.length());

            facts.add(fact);

            if (observations != null) {
              observations.add(String.format("%s -> p.%d : %s \n---\n%s\n---", doc.docId(),
                  pageIndex + 1, model.name(), snippet.replaceAll("(\r\n|\n)+", "\n")));
            }
          }
        }
      });
    }
    return facts;
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

    Fact fact = new Fact(factType, confidenceScore, null, new Date(), null, null);

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
