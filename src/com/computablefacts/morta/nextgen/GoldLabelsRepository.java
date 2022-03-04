package com.computablefacts.morta.nextgen;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.Document;
import com.computablefacts.asterix.View;
import com.computablefacts.asterix.codecs.JsonCodec;
import com.computablefacts.asterix.console.AsciiProgressBar;
import com.computablefacts.logfmt.LogFormatter;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.textcat.FingerPrint;
import com.computablefacts.morta.textcat.TextCategorizer;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class GoldLabelsRepository {

  private static final Logger logger_ = LoggerFactory.getLogger(GoldLabelsRepository.class);

  private final Map<String, List<IGoldLabel<String>>> goldLabels_ = new HashMap<>();
  private final Map<String, TextCategorizer> categorizers_ = new HashMap<>();

  private GoldLabelsRepository() {}

  public static GoldLabelsRepository fromFactsAndDocuments(File facts, File documents, String label,
      boolean withProgressBar) {

    Preconditions.checkNotNull(facts, "facts should not be null");
    Preconditions.checkArgument(facts.exists(), "facts file does not exist : %s", facts);
    Preconditions.checkNotNull(documents, "documents should not be null");
    Preconditions.checkArgument(documents.exists(), "documents file does not exist : %s",
        documents);

    GoldLabelsRepository repository = new GoldLabelsRepository();
    Set<IGoldLabel<String>> goldLabels =
        repository.loadGoldLabels(facts, documents, label, withProgressBar);
    Set<String> labels = goldLabels.stream().map(IGoldLabel::label).collect(Collectors.toSet());

    for (String lbl : labels) {
      repository.goldLabels_.put(lbl, goldLabels.stream()
          .filter(goldLabel -> lbl.equals(goldLabel.label())).collect(Collectors.toList()));
    }

    repository.labels()
        .forEach(lbl -> repository.categorizers_.put(lbl, repository.textCategorizer(lbl)));

    return repository;
  }

  public static GoldLabelsRepository fromGoldLabels(String outputDir, String label,
      boolean withProgressBar) {

    Preconditions.checkNotNull(outputDir, "outputDir should not be null");

    GoldLabelsRepository repository = new GoldLabelsRepository();
    File goldLabels = new File(outputDir + File.separator
        + (label == null ? "gold_labels.jsonl" : label + "_gold_labels.jsonl"));

    Preconditions.checkState(goldLabels.exists(), "goldLabels file does not exist : %s",
        goldLabels);

    AsciiProgressBar.ProgressBar progressBar = withProgressBar ? AsciiProgressBar.create() : null;
    AtomicInteger nbGoldLabelsTotal = new AtomicInteger(
        withProgressBar ? View.of(goldLabels).reduce(0, (carry, row) -> carry + 1) : 0);
    AtomicInteger nbGoldLabelsProcessed = new AtomicInteger(0);

    View.of(goldLabels).peek(entry -> {
      if (progressBar != null) {
        progressBar.update(nbGoldLabelsProcessed.incrementAndGet(), nbGoldLabelsTotal.get());
      }
    }).map(JsonCodec::asObject)
        .map(goldLabel -> new GoldLabel((Map<String, Object>) goldLabel.get("fact"),
            (Map<String, Object>) goldLabel.get("document")))
        .filter(goldLabel -> label == null || label.equals(goldLabel.label()))
        .forEachRemaining(repository::add);

    repository.labels()
        .forEach(lbl -> repository.categorizers_.put(lbl, repository.textCategorizer(lbl)));

    return repository;
  }

  public static GoldLabelsRepository fromProdigyAnnotations(String outputDir, String label,
      boolean withProgressBar) {

    Preconditions.checkNotNull(outputDir, "outputDir should not be null");

    GoldLabelsRepository repository = new GoldLabelsRepository();
    File annotations = new File(outputDir + File.separator
        + (label == null ? "prodigy_annotations.jsonl" : label + "_prodigy_annotations.jsonl"));

    Preconditions.checkState(annotations.exists(), "annotations file does not exist : %s",
        annotations);

    AsciiProgressBar.ProgressBar progressBar = withProgressBar ? AsciiProgressBar.create() : null;
    AtomicInteger nbAnnotationsTotal = new AtomicInteger(
        withProgressBar ? View.of(annotations).reduce(0, (carry, row) -> carry + 1) : 0);
    AtomicInteger nbAnnotationsProcessed = new AtomicInteger(0);

    View.of(annotations).peek(entry -> {
      if (progressBar != null) {
        progressBar.update(nbAnnotationsProcessed.incrementAndGet(), nbAnnotationsTotal.get());
      }
    }).map(JsonCodec::asObject).map(annotatedText -> new IGoldLabel<String>() {

      @Override
      public boolean equals(Object o) {
        if (o == this) {
          return true;
        }
        if (!(o instanceof IGoldLabel)) {
          return false;
        }
        IGoldLabel<String> gl = (IGoldLabel<String>) o;
        return Objects.equals(id(), gl.id()) && Objects.equals(label(), gl.label())
            && Objects.equals(data(), gl.data()) && Objects.equals(snippet(), gl.snippet())
            && Objects.equals(isTrueNegative(), gl.isTrueNegative())
            && Objects.equals(isTruePositive(), gl.isTruePositive())
            && Objects.equals(isFalseNegative(), gl.isFalseNegative())
            && Objects.equals(isFalsePositive(), gl.isFalsePositive());
      }

      @Override
      public int hashCode() {
        return Objects.hash(id(), label(), data(), snippet(), isTrueNegative(), isTruePositive(),
            isFalseNegative(), isFalsePositive());
      }

      @Override
      public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id()).add("label", label())
            .add("data", data()).add("snippet", snippet()).add("is_true_negative", isTrueNegative())
            .add("is_true_positive", isTruePositive()).add("is_false_negative", isFalseNegative())
            .add("is_false_positive", isFalsePositive()).omitNullValues().toString();
      }

      @Override
      public String id() {
        Map<String, Object> meta =
            (Map<String, Object>) annotatedText.getOrDefault("meta", new HashMap<>());
        return (String) meta.getOrDefault("source", "000|0000-00-00T00:00:00.000Z");
      }

      @Override
      public String label() {
        List<Map<String, Object>> spans =
            (List<Map<String, Object>>) annotatedText.getOrDefault("spans", new ArrayList<>());
        Map<String, Object> span = spans.isEmpty() ? null : spans.get(0);
        return span == null || !span.containsKey("label") ? "" : (String) span.get("label");
      }

      @Override
      public String data() {
        return (String) annotatedText.getOrDefault("text", "");
      }

      @Override
      public boolean isTruePositive() {
        return "accept".equals(annotatedText.getOrDefault("answer", "unknown"));
      }

      @Override
      public boolean isFalsePositive() {
        return false;
      }

      @Override
      public boolean isTrueNegative() {
        return "reject".equals(annotatedText.getOrDefault("answer", "unknown"));
      }

      @Override
      public boolean isFalseNegative() {
        return false;
      }

      @Override
      public String snippet() {
        List<Map<String, Object>> spans =
            (List<Map<String, Object>>) annotatedText.getOrDefault("spans", new ArrayList<>());
        Map<String, Object> span = spans.isEmpty() ? null : spans.get(0);
        return span == null ? "" : data().substring((int) span.get("start"), (int) span.get("end"));
      }
    }).filter(goldLabel -> goldLabel.isTruePositive() || goldLabel.isTrueNegative())
        .filter(goldLabel -> !Strings.isNullOrEmpty(goldLabel.snippet()))
        .filter(goldLabel -> label == null || label.equals(goldLabel.label()))
        .forEachRemaining(repository::add);

    repository.labels()
        .forEach(lbl -> repository.categorizers_.put(lbl, repository.textCategorizer(lbl)));

    return repository;
  }

  public Set<String> labels() {
    return goldLabels_.keySet();
  }

  public boolean isEmpty() {
    return goldLabels_.isEmpty();
  }

  public void add(IGoldLabel<String> goldLabel) {

    Preconditions.checkNotNull(goldLabel, "goldLabel should not be null");

    if (!goldLabels_.containsKey(goldLabel.label())) {
      goldLabels_.put(goldLabel.label(), new ArrayList<>());
    }
    goldLabels_.get(goldLabel.label()).add(goldLabel);
  }

  public void add(Collection<IGoldLabel<String>> goldLabels) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkArgument(!goldLabels.isEmpty(), "goldLabels should not be empty");

    goldLabels.forEach(this::add);
  }

  public Optional<List<IGoldLabel<String>>> goldLabels(String label) {
    return Optional.ofNullable(label == null
        ? goldLabels_.values().stream().flatMap(Collection::stream).collect(Collectors.toList())
        : goldLabels_.get(label));
  }

  public Optional<List<IGoldLabel<String>>> goldLabelsAccepted(String label) {
    return goldLabels(label).map(goldLabels -> goldLabels.stream()
        .filter(goldLabel -> goldLabel.isTruePositive() || goldLabel.isFalseNegative())
        .collect(Collectors.toList()));
  }

  public Optional<List<IGoldLabel<String>>> goldLabelsRejected(String label) {
    return goldLabels(label).map(goldLabels -> goldLabels.stream()
        .filter(goldLabel -> !goldLabel.isTruePositive() && !goldLabel.isFalseNegative())
        .collect(Collectors.toList()));
  }

  public Optional<TextCategorizer> categorizer(String label) {

    Preconditions.checkNotNull(label, "label should not be null");

    return Optional.ofNullable(categorizers_.get(label));
  }

  public Optional<ConfusionMatrix> categorizerConfusionMatrix(String label) {

    Preconditions.checkNotNull(label, "label should not be null");

    return categorizer(label).map(categorizer -> {

      ConfusionMatrix matrix = new ConfusionMatrix();

      goldLabelsAccepted(label).ifPresent(goldLabels -> goldLabels.forEach(goldLabel -> {

        String category =
            categorizer.categorize(goldLabel.snippet().replaceAll("(?s)[\\p{Zs}\\n\\r\\t]+", " "));

        if (!"unknown".equals(category)) {
          if ("ACCEPT".equals(category)) {
            matrix.addTruePositives(1);
          } else {
            matrix.addFalseNegatives(1);
          }
        }
      }));

      goldLabelsRejected(label).ifPresent(goldLabels -> goldLabels.forEach(goldLabel -> {

        String category =
            categorizer.categorize(goldLabel.snippet().replaceAll("(?s)[\\p{Zs}\\n\\r\\t]+", " "));

        if (!"unknown".equals(category)) {
          if ("ACCEPT".equals(category)) {
            matrix.addFalsePositives(1);
          } else {
            matrix.addTrueNegatives(1);
          }
        }
      }));
      return matrix;
    });
  }

  public void save(String outputDir, String label) {

    Preconditions.checkNotNull(outputDir, "outputDir should not be null");

    if (label == null) {
      File file = new File(outputDir + File.separator + "gold_labels.jsonl");
      View.of(goldLabels_.values()).flatten(View::of).toFile(JsonCodec::asString, file, false);
    } else {

      Optional<List<IGoldLabel<String>>> goldLabels = goldLabels(label);

      if (goldLabels.isPresent() && !goldLabels.get().isEmpty()) {

        File file = new File(outputDir + File.separator + label + "_gold_labels.jsonl");

        if (!file.exists()) {
          View.of(goldLabels.get()).toFile(JsonCodec::asString, file, false);
        }
      }
    }
  }

  public void export(String outputDir, String label) {

    Preconditions.checkNotNull(outputDir, "outputDir should not be null");

    if (label == null) {

      File file = new File(outputDir + File.separator + "prodigy_annotations.jsonl");
      View.of(goldLabels_.values()).flatten(View::of).map(IGoldLabel::annotatedText)
          .filter(Optional::isPresent).map(Optional::get).toFile(JsonCodec::asString, file, false);
    } else {

      Optional<List<IGoldLabel<String>>> goldLabels = goldLabels(label);

      if (goldLabels.isPresent() && !goldLabels.get().isEmpty()) {

        File file = new File(outputDir + File.separator + label + "_prodigy_annotations.jsonl");

        if (!file.exists()) {
          View.of(goldLabels.get()).map(IGoldLabel::annotatedText).filter(Optional::isPresent)
              .map(Optional::get).toFile(JsonCodec::asString, file, false);
        }
      }
    }
  }

  private Set<IGoldLabel<String>> loadGoldLabels(File facts, File documents, String label,
      boolean withProgressBar) {

    Preconditions.checkNotNull(facts, "facts should not be null");
    Preconditions.checkArgument(facts.exists(), "facts file does not exist : %s", facts);
    Preconditions.checkNotNull(documents, "documents should not be null");
    Preconditions.checkArgument(documents.exists(), "documents file does not exist : %s",
        documents);

    // Load facts and transform them to gold labels
    Set<GoldLabel> goldLabels = View.of(facts, true).filter(row -> !Strings.isNullOrEmpty(row))
        .map(JsonCodec::asObject).map(fact -> new GoldLabel(fact, null))
        .filter(goldLabel -> label == null || label.equals(goldLabel.label())).toSet();

    Set<String> docsIds = goldLabels.stream().map(GoldLabel::id).collect(Collectors.toSet());

    // Load documents and associate them with gold labels
    AsciiProgressBar.ProgressBar progressBar = withProgressBar ? AsciiProgressBar.create() : null;
    AtomicInteger nbGoldLabelsTotal = new AtomicInteger(goldLabels.size());
    AtomicInteger nbGoldLabelsProcessed = new AtomicInteger(0);

    return View.of(documents, true)
        .takeWhile(row -> !goldLabels
            .isEmpty() /* exit as soon as all gold labels are associated with a document */)
        .index().filter(row -> !Strings.isNullOrEmpty(row.getValue()) /* remove empty rows */)
        .map(row -> {
          try {
            return new Document(JsonCodec.asObject(row.getValue()));
          } catch (Exception ex) {
            logger_.error(LogFormatter.create(true).message(ex).add("line_number", row.getKey())
                .formatError());
          }
          return new Document("UNK");
        }).peek(doc -> {

          // Remove useless attributes
          doc.unindexedContent("bbox", null);
          doc.unindexedContent("tika", null);
        }).filter(doc -> {

          // Ignore empty documents
          if (doc.isEmpty()) {
            return false;
          }

          // Ignore non-pdf files
          if (!"application/pdf".equals(doc.contentType())) {
            return false;
          }

          // Ignore non-textual files
          if (!(doc.text() instanceof String)) {
            return false;
          }

          // Ignore documents that are not linked to at least one gold label
          return docsIds.contains(doc.docId());
        }).flatten(doc -> {

          // Associate the current document with the relevant gold labels
          Set<IGoldLabel<String>> gls =
              goldLabels.stream().filter(goldLabel -> goldLabel.id().equals(doc.docId()))
                  .peek(goldLabel -> goldLabel.document(doc)).collect(Collectors.toSet());

          // Remove the processed gold labels from the list of gold labels to be processed
          goldLabels.removeAll(gls);

          // Display progress
          if (progressBar != null) {
            progressBar.update(nbGoldLabelsProcessed.addAndGet(gls.size()),
                nbGoldLabelsTotal.get());
          }
          return View.of(gls);
        }).toSet();
  }

  private TextCategorizer textCategorizer(String label) {

    Preconditions.checkNotNull(label, "label should not be null");

    StringBuilder snippetsAccepted = new StringBuilder();
    List<IGoldLabel<String>> goldLabelsAccepted =
        goldLabelsAccepted(label).map(goldLabels -> goldLabels.stream()
            .filter(goldLabel -> !Strings.isNullOrEmpty(goldLabel.snippet()))
            .collect(Collectors.toList())).orElse(new ArrayList<>());
    double avgLengthAccepted = goldLabelsAccepted.stream()
        .peek(goldLabel -> snippetsAccepted
            .append(goldLabel.snippet().replaceAll("(?s)[\\p{Zs}\\n\\r\\t]+", " "))
            .append("\n\n\n"))
        .map(gl -> gl.snippet().length()).mapToInt(i -> i).average().orElse(0);

    StringBuilder snippetsRejected = new StringBuilder();
    List<IGoldLabel<String>> goldLabelsRejected =
        goldLabelsRejected(label).map(goldLabels -> goldLabels.stream()
            .filter(goldLabel -> !Strings.isNullOrEmpty(goldLabel.snippet()))
            .collect(Collectors.toList())).orElse(new ArrayList<>());
    double avgLengthRejected = goldLabelsRejected.stream()
        .peek(goldLabel -> snippetsRejected
            .append(goldLabel.snippet().replaceAll("(?s)[\\p{Zs}\\n\\r\\t]+", " "))
            .append("\n\n\n"))
        .map(gl -> gl.snippet().length()).mapToInt(i -> i).average().orElse(0);

    FingerPrint fpAccepted = new FingerPrint();
    fpAccepted.category("ACCEPT");
    fpAccepted.avgLength(avgLengthAccepted);
    fpAccepted.create(snippetsAccepted.toString());

    FingerPrint fpRejected = new FingerPrint();
    fpRejected.category("REJECT");
    fpRejected.avgLength(avgLengthRejected);
    fpRejected.create(snippetsRejected.toString());

    TextCategorizer textCategorizer = new TextCategorizer();
    textCategorizer.add(fpAccepted);
    textCategorizer.add(fpRejected);

    return textCategorizer;
  }
}
