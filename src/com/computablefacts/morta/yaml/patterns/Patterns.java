package com.computablefacts.morta.yaml.patterns;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.computablefacts.logfmt.LogFormatter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ObjectArrays;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

/**
 * Example of YAML file:
 *
 * <pre>
 * dependencies:
 *   - symbols.v0.yml
 *
 * fix_common_ocr_mistakes:
 *   - key: "i"
 *     value: "[il1t]"
 *   - key: "o"
 *     value: "[o0]"
 *   - key: "s"
 *     value: "[s5]"
 *
 * patterns:
 *   - name: SINGLE_WORD
 *     pattern: \p{L}+
 *     is_case_insensitive: true
 *     should_match:
 *       - word
 *       - WORD
 *       - Word
 *     should_not_match:
 *       - two words
 *       - word-with-hyphen
 *
 *   - name: MORE_THAN_ONE_WORD
 *     pattern: \p{L}+([-\s]\p{L}+)+
 *     is_case_insensitive: true
 *     should_match:
 *       - two words
 *       - word-with-hyphen
 *     should_not_match:
 *       - word
 *       - Word
 *       - WORD
 * </pre>
 */
@CheckReturnValue
final public class Patterns {

  private static final Logger logger_ = LoggerFactory.getLogger(Patterns.class);

  @JsonProperty("dependencies")
  public String[] dependencies_; // dependencies i.e. other YAML files this one depends on

  @JsonProperty("fix_common_ocr_mistakes")
  public com.computablefacts.morta.yaml.patterns.KeyValue[] fixCommonOcrMistakes_; // map a single
  // character to an array
  // of characters

  @JsonProperty("patterns")
  public Pattern[] patterns_;

  public Patterns() {}

  /**
   * Load patterns from YAML file.
   *
   * @param file YAML file to be loaded.
   * @param validate force file validation iif validate is true.
   * @return a list of patterns.
   */
  public static Pattern[] load(File file, boolean validate) {

    Preconditions.checkNotNull(file, "file should not be null");

    try {

      YAMLFactory yamlFactory = new YAMLFactory();
      YAMLMapper yamlMapper = new YAMLMapper(yamlFactory);
      yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      Patterns yaml = yamlMapper.readValue(file, Patterns.class);

      @Var
      Pattern[] dependencies = null;

      if (yaml.dependencies_ != null) {

        for (String dependency : yaml.dependencies_) {

          File f = new File(file.getParentFile().getAbsolutePath() + File.separator + dependency);
          Pattern[] pats = Patterns.load(f, validate);

          if (pats == null) {
            return null; // an invalid pattern has been found
          }

          dependencies =
              dependencies == null ? pats : ObjectArrays.concat(dependencies, pats, Pattern.class);
        }
      }

      yaml.fixSpelling();
      yaml.solve(dependencies);

      return validate && !yaml.isValid() ? null : yaml.patterns_;
    } catch (JsonProcessingException e) {
      logger_.error(LogFormatter.create().message(e).formatError());
    } catch (IOException e) {
      logger_.error(LogFormatter.create().message(e).formatError());
    }
    return null;
  }

  /**
   * Write YAML file.
   *
   * @param file YAML file to be written.
   * @param patterns a list of patterns.
   * @return true on success, false otherwise.
   */
  public static boolean dump(File file, Pattern[] patterns) {

    Preconditions.checkNotNull(file, "file should not be null");
    Preconditions.checkNotNull(patterns, "patterns should not be null");

    Patterns patternz = new Patterns();
    patternz.patterns_ = patterns;

    if (!patternz.isValid()) {
      return false;
    }

    try {
      YAMLFactory yamlFactory = new YAMLFactory();
      YAMLMapper yamlMapper = new YAMLMapper(yamlFactory);
      yamlMapper.writeValue(file, Patterns.class);

      return true;
    } catch (JsonProcessingException e) {
      logger_.error(LogFormatter.create().message(e).formatError());
    } catch (IOException e) {
      logger_.error(LogFormatter.create().message(e).formatError());
    }
    return false;
  }

  private boolean isValid() {
    for (int i = 0; patterns_ != null && i < patterns_.length; i++) {
      if (!patterns_[i].isValid()) {
        return false;
      }
    }
    return true;
  }

  private void fixSpelling() {

    if (fixCommonOcrMistakes_ == null) {
      return;
    }

    Map<String, String> map = new HashMap<>(fixCommonOcrMistakes_.length);

    for (com.computablefacts.morta.yaml.patterns.KeyValue yaml : fixCommonOcrMistakes_) {
      map.put(yaml.key_, yaml.value_);
    }

    for (Pattern pattern : patterns_) {
      pattern.pattern_ = fixSpelling(map, pattern.pattern_);
    }
  }

  private String fixSpelling(Map<String, String> map, String pattern) {

    Preconditions.checkNotNull(map, "map should not be null");
    Preconditions.checkNotNull(pattern, "pattern should not be null");

    @Var
    String newPattern = pattern;

    for (String key : map.keySet()) {
      newPattern = newPattern.replace(key, map.get(key));
    }
    return newPattern;
  }

  private void solve(Pattern[] dependencies) {
    if (dependencies != null) {
      for (Pattern pattern : patterns_) {
        pattern.pattern_ = solve(dependencies, pattern.pattern_);
      }
    }
  }

  private String solve(Pattern[] dependencies, String pattern) {

    Preconditions.checkNotNull(dependencies, "dependencies should not be null");
    Preconditions.checkNotNull(pattern, "pattern should not be null");

    @Var
    String newPattern = pattern;

    for (int i = 0; i < dependencies.length; i++) {

      String namePattern = "{" + dependencies[i].name_ + "}";

      if (newPattern.contains(namePattern)) {
        newPattern = newPattern.replace(namePattern, dependencies[i].pattern_);
      }
    }
    return newPattern;
  }
}
