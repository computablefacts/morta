package com.computablefacts.morta;

import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

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
import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.snorkel.ITransformationFunction;
import com.computablefacts.morta.snorkel.classifiers.AbstractClassifier;
import com.computablefacts.morta.snorkel.labelingfunctions.MatchWildcardLabelingFunction;
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
    File archive = getFileCommand(args, "archive", null);
    boolean showLogs = getBooleanCommand(args, "show_logs", false);
    int maxGroupSize = getIntCommand(args, "max_group_size", 4);
    String extractedWith = getStringCommand(args, "extracted_with", "morta");
    String extractedBy = getStringCommand(args, "extracted_by", "morta");
    String root = getStringCommand(args, "root", null);
    String dataset = getStringCommand(args, "dataset", null);
    String input = getStringCommand(args, "input", null);
    String output = getStringCommand(args, "output", null);
    double threshold = getDoubleCommand(args, "threshold", 0.7);

    Stopwatch stopwatch = Stopwatch.createStarted();

    // Loading model
    if (showLogs) {
      System.out.println("Loading model...");
    }

    Model model = new Model(language, label, maxGroupSize);

    if (!model.init(input)) {

      stopwatch.stop();

      if (showLogs) {
        System.out.println("Extraction failed : invalid model");
        System.out.println("Elapsed time : " + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " ms");
      }
    } else if (model.confidenceScore() < threshold) {

      stopwatch.stop();

      if (showLogs) {
        System.out.println("Extraction failed : confidence score is less than threshold ("
            + model.confidenceScore() + " < " + threshold + ")");
        System.out.println("Elapsed time : " + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " ms");
      }
    } else {

      if (showLogs) {
        System.out.printf("Alphabet size is %d\n", model.alphabet().size());
        System.out.printf("Model confidence score is %f\n", model.confidenceScore());
        System.out
            .println("Keywords found : [\n  " + Joiner.on("\n  ").join(model.keywords()) + "\n]");
        System.out.println("Processing documents...");
      }

      AtomicInteger nbExtractedFacts = new AtomicInteger(0);
      List<Fact> queue = new ArrayList<>(1000);
      List<Model> models = Lists.newArrayList(model);

      Files.compressedLineStream(archive, StandardCharsets.UTF_8)
          .filter(e -> !Strings.isNullOrEmpty(e.getValue()) /* skip empty rows */).map(e -> {
            try {
              return new Document(Codecs.asObject(e.getValue()));
            } catch (Exception ex) {
              // TODO :
              // logger_.error(Throwables.getStackTraceAsString(Throwables.getRootCause(ex)));
              // TODO : logger_.error("An error occurred on line : \"" + e.getKey() + "\"");
            }
            return null;
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

            List<Fact> facts =
                apply(extractedWith, extractedBy, root, dataset, models, doc, showLogs);

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

      stopwatch.stop();

      if (showLogs) {
        System.out.println("Number of extracted facts : " + nbExtractedFacts.get());
        System.out.println("Elapsed time : " + stopwatch.elapsed(TimeUnit.MILLISECONDS) + " ms");
      }
    }
  }

  public static List<Fact> apply(String extractedWith, String extractedBy, String root,
      String dataset, List<Model> models, Document doc, boolean showLogs) {

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

            if (showLogs) {
              System.out.printf("\n%s -> p.%d : %s \n---\n%s\n---", doc.docId(), pageIndex + 1,
                  model.name(), snippet.replaceAll("(\r\n|\n)+", "\n"));
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

  final public static class Model {

    private final XStream xStream_ = Helpers.xStream();
    private final String language_;
    private final String model_;
    private final int maxGroupSize_;
    private Dictionary alphabet_;
    private AbstractClassifier classifier_;
    private List<MatchWildcardLabelingFunction> labelingFunctions_;
    private List<String> keywords_;
    private double confidenceScore_;
    private ITransformationFunction<String, FeatureVector<Double>> countVectorizer_;

    public Model() {
      language_ = "";
      model_ = "";
      maxGroupSize_ = 0;
    }

    public Model(String language, String model, int maxGroupSize) {

      Preconditions.checkArgument(!Strings.isNullOrEmpty(language),
          "language is either null or empty");
      Preconditions.checkArgument(!Strings.isNullOrEmpty(model), "model is either null or empty");
      Preconditions.checkArgument(maxGroupSize > 0, "maxGroupSize must be > 0");

      language_ = language;
      model_ = model;
      maxGroupSize_ = maxGroupSize;
    }

    public String name() {
      return model_;
    }

    public String language() {
      return language_;
    }

    public Dictionary alphabet() {
      return alphabet_;
    }

    public AbstractClassifier classifier() {
      return classifier_;
    }

    public List<MatchWildcardLabelingFunction> labelingFunctions() {
      return labelingFunctions_;
    }

    public List<String> keywords() {
      return keywords_;
    }

    public double confidenceScore() {
      return confidenceScore_;
    }

    public ITransformationFunction<String, FeatureVector<Double>> countVectorizer() {
      return countVectorizer_;
    }

    public boolean isValid() {
      return !Strings.isNullOrEmpty(model_) && !Strings.isNullOrEmpty(language_)
          && maxGroupSize_ > 0 && alphabet_ != null && classifier_ != null
          && labelingFunctions_ != null && keywords_ != null && 0.0 <= confidenceScore_
          && confidenceScore_ <= 1.0 && countVectorizer_ != null;
    }

    public boolean init(String dir) {

      Preconditions.checkArgument(!Strings.isNullOrEmpty(dir),
          "dir should neither be null nor empty");

      alphabet_ = alphabet(dir);
      classifier_ = classifier(dir);
      labelingFunctions_ = labelingFunctions(dir);
      countVectorizer_ =
          Helpers.countVectorizer(Languages.eLanguage.valueOf(language_), alphabet_, maxGroupSize_);

      if (classifier_ != null) {
        if (Double.isNaN(classifier_.mcc()) || Double.isInfinite(classifier_.mcc())) {
          confidenceScore_ = -1.0;
        } else {
          confidenceScore_ = (classifier_.mcc() + 1.0) / 2.0; // Rescale MCC between 0 and 1
        }
      }
      if (labelingFunctions_ != null) {
        keywords_ = labelingFunctions_.stream().flatMap((lf) -> lf.literals().stream()).distinct()
            .collect(Collectors.toList());
      }
      return isValid();
    }

    private Dictionary alphabet(String dir) {
      return (Dictionary) xStream_
          .fromXML(Files
              .compressedLineStream(new File(Constants.alphabetGz(dir, language_, model_)),
                  StandardCharsets.UTF_8)
              .map(Map.Entry::getValue).collect(Collectors.joining("\n")));
    }

    private AbstractClassifier classifier(String dir) {
      return (AbstractClassifier) xStream_
          .fromXML(Files
              .compressedLineStream(new File(Constants.classifierGz(dir, language_, model_)),
                  StandardCharsets.UTF_8)
              .map(Map.Entry::getValue).collect(Collectors.joining("\n")));
    }

    private List<MatchWildcardLabelingFunction> labelingFunctions(String dir) {
      return (List<MatchWildcardLabelingFunction>) xStream_
          .fromXML(Files
              .compressedLineStream(new File(Constants.labelingFunctionsGz(dir, language_, model_)),
                  StandardCharsets.UTF_8)
              .map(Map.Entry::getValue).collect(Collectors.joining("\n")));
    }
  }
}
