package com.computablefacts.morta.nextgen;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.computablefacts.asterix.Document;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.spacy.AnnotatedText;
import com.computablefacts.morta.spacy.Meta;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GoldLabel implements IGoldLabel<String> {

  private static final char FORM_FEED = '\f';

  @JsonProperty(value = "fact", required = true)
  private final Map<String, Object> fact_;
  @JsonProperty(value = "document", required = true)
  private Map<String, Object> document_;

  @JsonCreator
  public GoldLabel(@JsonProperty(value = "fact") Map<String, Object> fact,
      @JsonProperty(value = "document") Map<String, Object> document) {
    fact_ = fact;
    document_ = document;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof GoldLabel)) {
      return false;
    }
    GoldLabel gl = (GoldLabel) o;
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
    return source().filter(map -> map.containsKey("doc_id")).map(map -> (String) map.get("doc_id"))
        .orElse("");
  }

  @Override
  public String label() {
    return (String) fact_.getOrDefault("type", "");
  }

  @Override
  public String data() {
    return pages().map(pages -> page().filter(page -> page > 0 && page <= pages.size())
        .map(page -> pages.get(page - 1)).orElse("")).orElse("");
  }

  @Override
  public boolean isTruePositive() {
    return isValid().orElse(false);
  }

  @Override
  public boolean isFalsePositive() {
    return false;
  }

  @Override
  public boolean isTrueNegative() {
    return !isValid().orElse(false);
  }

  @Override
  public boolean isFalseNegative() {
    return false;
  }

  @Override
  public String snippet() {
    return provenance()
        .filter(map -> startIndex().isPresent() && endIndex().isPresent()
            && map.containsKey("string_span"))
        .map(map -> (String) map.get("string_span"))
        .map(span -> span.substring(startIndex().orElse(0), endIndex().orElse(span.length())))
        .orElse("");
  }

  public void document(Document document) {

    Preconditions.checkNotNull(document, "document should not be null");

    document_ = document.json();
  }

  public Optional<Document> document() {
    return document_ == null ? Optional.empty() : Optional.of(new Document(document_));
  }

  /**
   * Output the Gold Label in <a href="https://prodi.gy/">Prodigy</a> data format for
   * <a href="https://prodi.gy/docs/text-classification">text classification</a> or
   * <a href="https://prodi.gy/docs/span-categorization">span categorization</a>.
   *
   * @return an {@link AnnotatedText}.
   */
  public Optional<AnnotatedText> annotatedText() {

    if (!isTruePositive() && !isTrueNegative() && !isFalsePositive() && !isFalseNegative()) {
      return Optional.empty();
    }

    boolean accept = isTruePositive() || isFalseNegative();
    Meta meta = new Meta(id(), label(), accept ? "accept" : "reject",
        !Strings.isNullOrEmpty(snippet()) && data().contains(snippet()) ? snippet() : null);
    AnnotatedText annotatedText = new AnnotatedText(meta, data());

    return Optional.of(annotatedText);
  }

  private Optional<List<String>> pages() {
    return document().map(doc -> (String) doc.text())
        .map(text -> Splitter.on(FORM_FEED).splitToList(text));
  }

  private Optional<Integer> page() {
    return values()
        .map(list -> list.size() == 5 /* vam */ ? Integer.parseInt(list.get(1), 10)
            : list.size() == 3 /* dab */ ? Integer.parseInt(list.get(2), 10) : 0)
        .filter(page -> page > 0 /* page is 1-based */);
  }

  private Optional<Boolean> isValid() {
    return Optional.ofNullable((Boolean) fact_.get("is_valid"));
  }

  private Optional<Integer> startIndex() {
    return provenance().filter(map -> map.containsKey("start_index"))
        .map(map -> (Integer) map.get("start_index"));
  }

  private Optional<Integer> endIndex() {
    return provenance().filter(map -> map.containsKey("end_index"))
        .map(map -> (Integer) map.get("end_index"));
  }

  private Optional<List<String>> values() {
    return Optional.ofNullable((List<String>) fact_.get("values"));
  }

  private Optional<List<Map<String, Object>>> metadata() {
    return Optional.ofNullable((List<Map<String, Object>>) fact_.get("metadata"));
  }

  private Optional<Map<String, Object>> source() {
    return provenance().filter(map -> map.containsKey("source"))
        .map(map -> (Map<String, Object>) map.get("source"));
  }

  private Optional<Map<String, Object>> provenance() {
    return provenances().filter(list -> !list.isEmpty()).map(list -> list.get(0));
  }

  private Optional<List<Map<String, Object>>> provenances() {
    return Optional.ofNullable((List<Map<String, Object>>) fact_.get("provenances"));
  }
}
