package com.computablefacts.morta;

import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.asterix.Document;
import com.computablefacts.asterix.SnippetExtractor;
import com.computablefacts.asterix.View;
import com.computablefacts.asterix.codecs.JsonCodec;
import com.computablefacts.asterix.console.ConsoleApp;
import com.computablefacts.junon.Fact;
import com.computablefacts.junon.Metadata;
import com.computablefacts.junon.Provenance;
import com.computablefacts.morta.snorkel.spacy.AnnotatedText;
import com.computablefacts.morta.snorkel.spacy.Meta;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class ExecuteModel extends ConsoleApp {

  private static final Logger logger_ = LoggerFactory.getLogger(ExecuteModel.class);
  private static final char FORM_FEED = '\f';

  public static void main(String[] args) {

    String language = getStringCommand(args, "language", null);
    String label = getStringCommand(args, "label", null);
    File archive = getFileCommand(args, "archive", null);
    int maxGroupSize = getIntCommand(args, "max_group_size", 1);
    String extractedWith = getStringCommand(args, "extracted_with", "morta");
    String extractedBy = getStringCommand(args, "extracted_by", "morta");
    String root = getStringCommand(args, "root", null);
    String dataset = getStringCommand(args, "dataset", null);
    String input = getStringCommand(args, "input", null);
    String output = getStringCommand(args, "output", null);
    double threshold = getDoubleCommand(args, "threshold", 0.5);
    boolean verbose = getBooleanCommand(args, "verbose", false);
    String format = getStringCommand(args, "format", "facts");
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
      observations.add("Processing documents...");

      AtomicInteger nbExtractedFacts = new AtomicInteger(0);
      List<Model> models = Lists.newArrayList(model);

      View.of(archive, true).index()
          .filter(e -> !Strings.isNullOrEmpty(e.getValue()) /* skip empty rows */).peek(e -> {
            if (e.getKey() % 100 == 0) {
              System.out.println(String.format("%d documents processed...", e.getKey()));
            }
          }).map(e -> {
            try {
              return new Document(JsonCodec.asObject(e.getValue()));
            } catch (Exception ex) {
              logger_.error(Throwables.getStackTraceAsString(Throwables.getRootCause(ex)));
              logger_.error(String.format("An error occurred on line : \"%s\"", e.getKey()));
            }
            return null;
          }).filter(Objects::nonNull)
          .map(doc -> apply(verbose ? observations : null, extractedWith, extractedBy, root,
              dataset, models, doc))
          .filter(facts -> !facts.isEmpty()).peek(facts -> nbExtractedFacts.addAndGet(facts.size()))
          .flatten(View::of).map(fact -> {
            if (!"spacy".equals(format)) {
              return fact;
            }
            Meta meta = new Meta(fact.provenances_.get(0).sourceStore_, fact.type_, "accept");
            return new AnnotatedText(meta, fact.provenances_.get(0).span_);
          }).toFile(JsonCodec::asString, new File(output), false);

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

          List<String> keywords = model.keywords(page);

          if (!keywords.isEmpty()) {

            String snippet = SnippetExtractor.extract(keywords, page, 300, 50, "");

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
    fact.provenance(new Provenance(sourceType, sourceStore, null, null, null, page, string, span,
        startIndex, endIndex));

    return fact;
  }
}
