package com.computablefacts.morta.nextgen2;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.asterix.Document;
import com.computablefacts.asterix.View;
import com.computablefacts.asterix.codecs.JsonCodec;
import com.computablefacts.asterix.console.AsciiProgressBar;
import com.computablefacts.logfmt.LogFormatter;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;

/**
 * Link a {@link com.computablefacts.junon.Fact}, i.e. a span of text associated with a label, to
 * its underlying {@link com.computablefacts.asterix.Document}, i.e. the document from which the
 * fact has been extracted.
 */
@CheckReturnValue
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FactAndDocument {

  private static final char FORM_FEED = '\f';
  private static final Logger logger_ = LoggerFactory.getLogger(FactAndDocument.class);

  @JsonProperty(value = "fact", required = true)
  private final Map<String, Object> fact_;
  @JsonProperty(value = "document", required = true)
  private Map<String, Object> document_;

  private FactAndDocument(Map<String, Object> fact) {
    this(fact, null);
  }

  @JsonCreator
  private FactAndDocument(@JsonProperty(value = "fact") Map<String, Object> fact,
      @JsonProperty(value = "document") Map<String, Object> document) {

    Preconditions.checkNotNull(fact, "fact should not be null");

    fact_ = fact;
    document_ = document;
  }

  /**
   * Load elements from a gzipped JSONL file.
   *
   * @param file the input file.
   * @param label the specific gold labels to load. If {@code label} is set to {@code null}, all
   *        gold labels will be loaded.
   * @param withProgressBar true iif a progress bar should be displayed, false otherwise.
   * @return a set of elements.
   */
  @SuppressWarnings("unchecked")
  public static Set<FactAndDocument> load(File file, String label, boolean withProgressBar) {

    Preconditions.checkNotNull(file, "file should not be null");
    Preconditions.checkArgument(file.exists(), "file file does not exist : %s", file);

    AsciiProgressBar.ProgressBar progressBar = withProgressBar ? AsciiProgressBar.create() : null;
    AtomicInteger nbElements = new AtomicInteger(
        withProgressBar ? View.of(file, true).reduce(0, (carry, row) -> carry + 1) : 0);

    return View.of(file, true).index()
        .filter(row -> !Strings.isNullOrEmpty(row.getValue()) /* remove empty rows */).map(row -> {

          if (progressBar != null) {
            progressBar.update(row.getKey(), nbElements.get());
          }

          Map<String, Object> element = JsonCodec.asObject(row.getValue());
          Map<String, Object> fact = (Map<String, Object>) element.get("fact");
          Map<String, Object> document = (Map<String, Object>) element.get("document");

          return new FactAndDocument(fact, document);
        }).filter(element -> label == null || label.equals(element.label())).toSet();
  }

  /**
   * Load elements from raw gzipped JSONL files.
   *
   * @param facts the 'fact' file.
   * @param documents the 'document' file.
   * @param label the specific gold labels to load. If {@code label} is set to {@code null}, all
   *        gold labels will be loaded.
   * @param withProgressBar true iif a progress bar should be displayed, false otherwise.
   * @return a set of elements.
   */
  public static Set<FactAndDocument> load(File facts, File documents, String label,
      boolean withProgressBar) {

    Preconditions.checkNotNull(facts, "facts should not be null");
    Preconditions.checkArgument(facts.exists(), "facts file does not exist : %s", facts);
    Preconditions.checkNotNull(documents, "documents should not be null");
    Preconditions.checkArgument(documents.exists(), "documents file does not exist : %s",
        documents);

    // Load facts
    Set<FactAndDocument> elements =
        View.of(facts, true).filter(row -> !Strings.isNullOrEmpty(row) /* remove empty rows */)
            .map(JsonCodec::asObject).map(FactAndDocument::new)
            .filter(element -> label == null || label.equals(element.label())).toSet();

    Set<String> docsIds = elements.stream().map(FactAndDocument::id).collect(Collectors.toSet());

    // Load documents and associate them with facts
    AsciiProgressBar.ProgressBar progressBar = withProgressBar ? AsciiProgressBar.create() : null;
    AtomicInteger nbElementsTotal = new AtomicInteger(elements.size());
    AtomicInteger nbElements = new AtomicInteger(0);

    return View.of(documents, true).takeWhile(
        row -> !elements.isEmpty() /* exit as soon as all facts are associated with a document */)
        .filter(row -> !Strings.isNullOrEmpty(row) /* remove empty rows */).map(row -> {
          try {
            return new Document(JsonCodec.asObject(row));
          } catch (Exception ex) {
            logger_
                .error(LogFormatter.create(true).message(ex).add("line_number", row).formatError());
          }
          return new Document("UNK");
        }).peek(doc -> {

          // Remove useless document attributes
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

          // Ignore documents that are not linked to at least one fact
          return docsIds.contains(doc.docId());
        }).flatten(doc -> {

          // Associate the current document with the relevant facts
          Set<FactAndDocument> els =
              elements.stream().filter(element -> element.id().equals(doc.docId()))
                  .peek(element -> element.document(doc)).collect(Collectors.toSet());

          // Remove the processed facts from the list of facts to be processed
          elements.removeAll(els);

          // Update progress bar
          if (progressBar != null) {
            progressBar.update(nbElements.addAndGet(els.size()), nbElementsTotal.get());
          }
          return View.of(els);
        }).toSet();
  }

  /**
   * Save elements to a gzipped JSONL file.
   *
   * @param file the output file.
   * @param elements the elements to save.
   * @return true iif the elements have been written to the file, false otherwise.
   */
  @CanIgnoreReturnValue
  public static boolean save(File file, Collection<FactAndDocument> elements) {

    Preconditions.checkNotNull(file, "file should not be null");
    Preconditions.checkNotNull(elements, "elements should not be null");

    if (!file.exists()) {
      if (!elements.isEmpty()) {
        View.of(elements).toFile(JsonCodec::asString, file, false, true);
      }
      return true;
    }
    return false;
  }

  /**
   * Returns each accepted or rejected fact's underlying page as a gold label.
   *
   * @param elements a set of elements.
   * @return a set of gold labels.
   */
  public static Set<IGoldLabel<String>> pagesAsGoldLabels(Collection<FactAndDocument> elements) {

    Preconditions.checkNotNull(elements, "elements should not be null");

    return elements.stream().filter(element -> element.isAccepted() || element.isRejected())
        .map(FactAndDocument::pageAsGoldLabel).collect(Collectors.toSet());
  }

  /**
   * Returns each accepted or rejected fact as a gold label.
   * 
   * @param elements a set of elements.
   * @return a set of gold labels.
   */
  public static Set<IGoldLabel<String>> factsAsGoldLabels(Collection<FactAndDocument> elements) {

    Preconditions.checkNotNull(elements, "elements should not be null");

    return elements.stream().filter(element -> element.isAccepted() || element.isRejected())
        .map(FactAndDocument::factAsGoldLabel).collect(Collectors.toSet());
  }

  /**
   * For each accepted fact returns the unmatched pages as 'true negative' gold labels.
   *
   * @param elements a set of elements.
   * @return a set of gold labels.
   */
  public static Set<IGoldLabel<String>> syntheticGoldLabels(Collection<FactAndDocument> elements) {

    Preconditions.checkNotNull(elements, "elements should not be null");

    return elements.stream().filter(FactAndDocument::isAccepted)
        .flatMap(element -> element.syntheticGoldLabels().stream()).collect(Collectors.toSet());
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FactAndDocument)) {
      return false;
    }
    FactAndDocument obj = (FactAndDocument) o;
    return Objects.equals(fact_, obj.fact_) && Objects.equals(document_, obj.document_);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fact_, document_);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("fact", fact_).add("document", document_)
        .omitNullValues().toString();
  }

  /**
   * Returns the fact's underlying document (if any).
   *
   * @return a well-formed document if any, an exception otherwise.
   */
  public Document document() {

    Preconditions.checkState(document_ != null, "document should not be null");

    return new Document(document_);
  }

  /**
   * Returns the fact's underlying document identifier (if any).
   *
   * @return the fact's underlying document identifier if any, an empty string otherwise.
   */
  public String id() {
    return source().filter(map -> map.containsKey("doc_id")).map(map -> (String) map.get("doc_id"))
        .orElse("");
  }

  /**
   * Returns the extracted fact name.
   *
   * @return the fact name.
   */
  public String label() {
    return (String) fact_.getOrDefault("type", "");
  }

  /**
   * Returns the page that contains the extracted fact.
   *
   * @return a single page if any, an empty string otherwise.
   */
  public String matchedPage() {
    return pages().map(pages -> page().filter(page -> page > 0 && page <= pages.size())
        .map(page -> pages.get(page - 1 /* page is 1-based */)).orElse("")).orElse("");
  }

  /**
   * Returns the list of pages that do not contain the extracted fact.
   *
   * @return a list of pages if any, an empty list otherwise.
   */
  public List<String> unmatchedPages() {
    return pages().map(pages -> {

      List<String> newPages = new ArrayList<>(pages);
      page().filter(page -> page > 0 && page <= newPages.size())
          .ifPresent(page -> newPages.remove(page - 1 /* page is 1-based */));

      return newPages;
    }).orElse(new ArrayList<>());
  }

  /**
   * Returns the extracted fact i.e. the span of text extracted from {@link #matchedPage()}.
   *
   * Note that it does not imply that {@code matchedPage().indexOf(snippet()) >= 0}.
   *
   * @return a text fragment if any, an empty string otherwise.
   */
  public String fact() {
    return provenance()
        .filter(map -> startIndex().isPresent() && endIndex().isPresent()
            && map.containsKey("string_span"))
        .map(map -> (String) map.get("string_span"))
        .map(span -> span.substring(startIndex().orElse(0), endIndex().orElse(span.length())))
        .orElse("");
  }

  /**
   * Returns true iif the fact has been accepted.
   *
   * @return true if the fact has been accepted, false otherwise.
   */
  public boolean isAccepted() {
    Boolean isValid = (Boolean) fact_.get("is_valid");
    return isValid != null && isValid;
  }

  /**
   * Returns true iif the fact has been rejected.
   *
   * @return true if the fact has been rejected, false otherwise.
   */
  public boolean isRejected() {
    Boolean isValid = (Boolean) fact_.get("is_valid");
    return isValid != null && !isValid;
  }

  /**
   * Returns true iif the fact should be verified.
   *
   * @return true if the fact should be verified, false otherwise.
   */
  public boolean isVerified() {
    Boolean isValid = (Boolean) fact_.get("is_valid");
    return isValid != null;
  }

  /**
   * Returns the fact's underlying page as a gold label.
   *
   * @return a gold label.
   */
  public IGoldLabel<String> pageAsGoldLabel() {

    Preconditions.checkState(isAccepted() || isRejected(),
        "unverified facts cannot be treated as gold labels");

    return new GoldLabelOfString(id(), label(), matchedPage(), isRejected(), isAccepted(), false,
        false);
  }

  /**
   * Returns the fact as a gold label.
   *
   * @return a gold label.
   */
  public IGoldLabel<String> factAsGoldLabel() {

    Preconditions.checkState(isAccepted() || isRejected(),
        "unverified facts cannot be treated as gold labels");

    return new GoldLabelOfString(id(), label(), fact(), isRejected(), isAccepted(), false, false);
  }

  /**
   * If the current fact has been accepted, returns unmatched pages as 'true negative' gold labels.
   *
   * @return a set of synthetic gold labels.
   */
  public Set<IGoldLabel<String>> syntheticGoldLabels() {

    Preconditions.checkState(isAccepted(),
        "unverified or rejected facts cannot be used to create synthetic gold labels");

    return unmatchedPages().stream()
        .map(page -> new GoldLabelOfString(id(), label(), page, true, false, false, false))
        .collect(Collectors.toSet());
  }

  /**
   * Set the fact's underlying document.
   *
   * @param document a well-formed document.
   */
  private void document(Document document) {

    Preconditions.checkNotNull(document, "document should not be null");

    document_ = document.json();
  }

  private Optional<List<String>> pages() {
    return Optional.ofNullable(document_).map(doc -> document()).map(doc -> (String) doc.text())
        .map(text -> Splitter.on(FORM_FEED).splitToList(text));
  }

  private Optional<Integer> page() {
    return values()
        .map(list -> list.size() == 5 /* vam */ ? Integer.parseInt(list.get(1), 10)
            : list.size() == 3 /* dab */ ? Integer.parseInt(list.get(2), 10) : 0)
        .filter(page -> page > 0 /* page is 1-based */);
  }

  private Optional<Integer> startIndex() {
    return provenance().filter(map -> map.containsKey("start_index"))
        .map(map -> (Integer) map.get("start_index"));
  }

  private Optional<Integer> endIndex() {
    return provenance().filter(map -> map.containsKey("end_index"))
        .map(map -> (Integer) map.get("end_index"));
  }

  @SuppressWarnings("unchecked")
  private Optional<List<String>> values() {
    return Optional.ofNullable((List<String>) fact_.get("values"));
  }

  @SuppressWarnings("unchecked")
  private Optional<List<Map<String, Object>>> metadata() {
    return Optional.ofNullable((List<Map<String, Object>>) fact_.get("metadata"));
  }

  @SuppressWarnings("unchecked")
  private Optional<Map<String, Object>> source() {
    return provenance().filter(map -> map.containsKey("source"))
        .map(map -> (Map<String, Object>) map.get("source"));
  }

  private Optional<Map<String, Object>> provenance() {
    return provenances().filter(list -> !list.isEmpty()).map(list -> list.get(0));
  }

  @SuppressWarnings("unchecked")
  private Optional<List<Map<String, Object>>> provenances() {
    return Optional.ofNullable((List<Map<String, Object>>) fact_.get("provenances"));
  }
}
