package com.computablefacts.morta.snorkel;

import java.util.List;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import com.computablefacts.nona.helpers.Languages;
import com.google.common.collect.Lists;

public class HelpersTest {

  @Test(expected = NullPointerException.class)
  public void testSentenceSplitterSplitsNull() {

    Function<String, List<String>> sentenceSplitter = Helpers.sentenceSplitter();

    List<String> sentences = sentenceSplitter.apply(null);
  }

  @Test
  public void testSentenceSplitterSplitsEmpty() {

    Function<String, List<String>> sentenceSplitter = Helpers.sentenceSplitter();

    Assert.assertTrue(sentenceSplitter.apply("").isEmpty());
  }

  @Test
  public void testSentenceSplitterSplitsWhitespaces() {

    Function<String, List<String>> sentenceSplitter = Helpers.sentenceSplitter();

    Assert.assertTrue(sentenceSplitter.apply("\t\n\r\u00a0").isEmpty());
  }

  @Test(expected = NullPointerException.class)
  public void testWordSplitterSplitsNull() {

    Function<String, List<String>> wordSplitter = Helpers.wordSplitter(Languages.eLanguage.FRENCH);

    List<String> words = wordSplitter.apply(null);
  }

  @Test
  public void testWordSplitterSplitsEmpty() {

    Function<String, List<String>> wordSplitter = Helpers.wordSplitter(Languages.eLanguage.FRENCH);

    Assert.assertTrue(wordSplitter.apply("").isEmpty());
  }

  @Test
  public void testWordSplitterSplitsWhitespaces() {

    Function<String, List<String>> wordSplitter = Helpers.wordSplitter(Languages.eLanguage.FRENCH);

    Assert.assertTrue(wordSplitter.apply("\t\n\r\u00a0").isEmpty());
  }

  @Test
  public void testWordSplitter() {

    Function<String, List<String>> wordSplitter = Helpers.wordSplitter(Languages.eLanguage.FRENCH);

    Assert.assertEquals(
        Lists.newArrayList("Monaco", "vid", "fourri", ":", "de", "voitur", "des", "30", "€",
            "propos", "lor", "d'un", "vent", "enchères."),
        wordSplitter.apply(
            "Monaco vide sa fourrière : des voitures dès 30 € proposées lors d'une vente aux enchères."));
  }
}
