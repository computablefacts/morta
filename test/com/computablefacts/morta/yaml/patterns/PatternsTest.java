package com.computablefacts.morta.yaml.patterns;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

public class PatternsTest {

  @Test
  public void testDump() throws IOException {

    Pattern[] patterns = new Pattern[1];
    patterns[0] = new Pattern();

    patterns[0].name_ = "SINGLE_WORD";
    patterns[0].pattern_ = "\\p{L}+";
    patterns[0].shouldMatch_ = new String[2];
    patterns[0].shouldMatch_[0] = "WORD";
    patterns[0].shouldMatch_[1] = "word";

    patterns[0].shouldNotMatch_ = new String[2];
    patterns[0].shouldNotMatch_[0] = "two words";
    patterns[0].shouldNotMatch_[1] = "word-with-hyphen";

    // Dump YAML
    Path file = Files.createTempFile("patterns-", ".yml");

    Assert.assertTrue(Patterns.dump(file.toFile(), patterns));

    // Try to reload the dumped file
    Pattern[] patternz = Patterns.load(file.toFile(), true);

    Assert.assertEquals(1, patterns.length);

    Assert.assertEquals("SINGLE_WORD", patterns[0].name_);
    Assert.assertNull(patterns[0].description_);
    Assert.assertEquals("\\p{L}+", patterns[0].pattern_);
    Assert.assertEquals(2, patterns[0].shouldMatch_.length);
    Assert.assertEquals("WORD", patterns[0].shouldMatch_[0]);
    Assert.assertEquals("word", patterns[0].shouldMatch_[1]);
    Assert.assertEquals(2, patterns[0].shouldNotMatch_.length);
    Assert.assertEquals("two words", patterns[0].shouldNotMatch_[0]);
    Assert.assertEquals("word-with-hyphen", patterns[0].shouldNotMatch_[1]);
  }

  @Test
  public void testPatterns() throws IOException {

    String yaml = "patterns:\n" + "  - name: SINGLE_WORD\n" + "    pattern: \\p{L}+\n"
        + "    should_match:\n" + "      - word\n" + "      - WORD\n" + "      - Word\n"
        + "    should_not_match:\n" + "      - two words\n" + "      - word-with-hyphen\n"
        + "  - name: MORE_THAN_ONE_WORD\n" + "    pattern: \\p{L}+([-\\s]\\p{L}+)+\n"
        + "    should_match:\n" + "      - two words\n" + "      - word-with-hyphen\n"
        + "    should_not_match:\n" + "      - word\n" + "      - Word\n" + "      - WORD";

    Path file = Files.createTempFile("patterns-", ".yml");
    java.nio.file.Files.write(file, Lists.newArrayList(yaml), StandardCharsets.UTF_8,
        StandardOpenOption.CREATE);

    Pattern[] patterns = Patterns.load(file.toFile(), true);

    Assert.assertEquals(2, patterns.length);

    Assert.assertEquals("SINGLE_WORD", patterns[0].name_);
    Assert.assertNull(patterns[0].description_);
    Assert.assertEquals("\\p{L}+", patterns[0].pattern_);
    Assert.assertEquals(3, patterns[0].shouldMatch_.length);
    Assert.assertEquals("word", patterns[0].shouldMatch_[0]);
    Assert.assertEquals("WORD", patterns[0].shouldMatch_[1]);
    Assert.assertEquals("Word", patterns[0].shouldMatch_[2]);
    Assert.assertEquals(2, patterns[0].shouldNotMatch_.length);
    Assert.assertEquals("two words", patterns[0].shouldNotMatch_[0]);
    Assert.assertEquals("word-with-hyphen", patterns[0].shouldNotMatch_[1]);

    Assert.assertEquals("MORE_THAN_ONE_WORD", patterns[1].name_);
    Assert.assertNull(patterns[1].description_);
    Assert.assertEquals("\\p{L}+([-\\s]\\p{L}+)+", patterns[1].pattern_);
    Assert.assertEquals(2, patterns[1].shouldMatch_.length);
    Assert.assertEquals("two words", patterns[1].shouldMatch_[0]);
    Assert.assertEquals("word-with-hyphen", patterns[1].shouldMatch_[1]);
    Assert.assertEquals(3, patterns[1].shouldNotMatch_.length);
    Assert.assertEquals("word", patterns[1].shouldNotMatch_[0]);
    Assert.assertEquals("Word", patterns[1].shouldNotMatch_[1]);
    Assert.assertEquals("WORD", patterns[1].shouldNotMatch_[2]);
  }

  @Test
  public void testFixCommonOcrMistakes() throws IOException {

    String yaml = "fix_common_ocr_mistakes:\n" + "  - key: \"i\"\n" + "    value: \"[il1t]\"\n"
        + "  - key: \"o\"\n" + "    value: \"[o0]\"\n" + "  - key: \"s\"\n"
        + "    value: \"[s5]\"\n" + "patterns:\n" + "  - name: APPROVISIONNEMENT\n"
        + "    pattern: (?:approvisionnements?)\n" + "    should_match:\n"
        + "      - approvisionnement\n" + "      - approvisionnements\n"
        + "      - approv1s10nnement\n" + "      - approv1s10nnement5";

    Path file = Files.createTempFile("patterns-", ".yml");
    java.nio.file.Files.write(file, Lists.newArrayList(yaml), StandardCharsets.UTF_8,
        StandardOpenOption.CREATE);

    Pattern[] patterns = Patterns.load(file.toFile(), true);

    Assert.assertEquals(1, patterns.length);

    Assert.assertEquals("APPROVISIONNEMENT", patterns[0].name_);
    Assert.assertNull(patterns[0].description_);
    Assert.assertEquals("(?:appr[o0]v[il1t][s5][il1t][o0]nnement[s5]?)", patterns[0].pattern_);
    Assert.assertEquals(4, patterns[0].shouldMatch_.length);
    Assert.assertEquals("approvisionnement", patterns[0].shouldMatch_[0]);
    Assert.assertEquals("approvisionnements", patterns[0].shouldMatch_[1]);
    Assert.assertEquals("approv1s10nnement", patterns[0].shouldMatch_[2]);
    Assert.assertEquals("approv1s10nnement5", patterns[0].shouldMatch_[3]);
  }

  @Test
  public void testDependencies() throws IOException {

    String yaml1 = "patterns:\n" + "  - name: W\n" + "    pattern: (?:\\p{Zs})\n";

    Path file1 = Files.createTempFile("patterns-", ".yml");
    java.nio.file.Files.write(file1, Lists.newArrayList(yaml1), StandardCharsets.UTF_8,
        StandardOpenOption.CREATE);

    String yaml2 = "dependencies:\n" + "  - " + file1.getFileName() + "\npatterns:\n"
        + "  - name: ACQUERIR_UNE_GARANTIE\n"
        + "    pattern: (?:la{W}+garantie{W}+.*{W}+est{W}+acquise)\n" + "    should_match:\n"
        + "      - La garantie de la Société est acquise\n";

    Path file2 = Files.createTempFile("patterns-", ".yml");
    java.nio.file.Files.write(file2, Lists.newArrayList(yaml2), StandardCharsets.UTF_8,
        StandardOpenOption.CREATE);

    Pattern[] patterns = Patterns.load(file2.toFile(), true);

    Assert.assertEquals(1, patterns.length);

    Assert.assertEquals("ACQUERIR_UNE_GARANTIE", patterns[0].name_);
    Assert.assertNull(patterns[0].description_);
    Assert.assertEquals(
        "(?:la(?:\\p{Zs})+garantie(?:\\p{Zs})+.*(?:\\p{Zs})+est(?:\\p{Zs})+acquise)",
        patterns[0].pattern_);
    Assert.assertEquals(1, patterns[0].shouldMatch_.length);
    Assert.assertEquals("La garantie de la Société est acquise", patterns[0].shouldMatch_[0]);
  }

  @Test
  public void testWildcard() throws IOException {

    String yaml = "patterns:\n" + "  - name: DATE_YYYY_MM_DD\n" + "    pattern: ????-??-??\n"
        + "    is_wildcard: true\n" + "    should_match:\n" + "      - 2020-11-10\n"
        + "    should_not_match:\n" + "      - 2020/11/10\n";

    Path file = Files.createTempFile("patterns-", ".yml");
    java.nio.file.Files.write(file, Lists.newArrayList(yaml), StandardCharsets.UTF_8,
        StandardOpenOption.CREATE);

    Pattern[] patterns = Patterns.load(file.toFile(), true);

    Assert.assertEquals(1, patterns.length);

    Assert.assertEquals("DATE_YYYY_MM_DD", patterns[0].name_);
    Assert.assertNull(patterns[0].description_);
    Assert.assertEquals("????-??-??", patterns[0].pattern_);
    Assert.assertEquals(1, patterns[0].shouldMatch_.length);
    Assert.assertEquals("2020-11-10", patterns[0].shouldMatch_[0]);
    Assert.assertEquals(1, patterns[0].shouldNotMatch_.length);
    Assert.assertEquals("2020/11/10", patterns[0].shouldNotMatch_[0]);
  }
}
