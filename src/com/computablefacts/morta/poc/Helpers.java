package com.computablefacts.morta.poc;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.tartarus.snowball.SnowballStemmer;

import com.computablefacts.nona.helpers.Languages;
import com.computablefacts.nona.helpers.StringIterator;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.errorprone.annotations.CheckReturnValue;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;

@CheckReturnValue
final public class Helpers {

  private Helpers() {}

  public static XStream xStream() {

    XStream xStream = new XStream();
    xStream.addPermission(NoTypePermission.NONE);
    xStream.addPermission(NullPermission.NULL);
    xStream.addPermission(PrimitiveTypePermission.PRIMITIVES);
    xStream.allowTypeHierarchy(Collection.class);
    xStream.allowTypesByWildcard(new String[] {"com.computablefacts.**",
        "com.google.common.collect.**", "java.lang.**", "java.util.**", "smile.classification.**"});

    return xStream;
  }

  public static DecimalFormat decimalFormat() {

    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
    symbols.setDecimalSeparator('.');

    DecimalFormat df = new DecimalFormat("#.##", symbols);
    df.setGroupingUsed(false);
    df.setRoundingMode(RoundingMode.UP);

    return df;
  }

  public static Function<String, List<String>> sentenceSplitter() {
    return s -> Splitter.on(CharMatcher.anyOf("!?.,;:")).trimResults().omitEmptyStrings()
        .splitToList(normalize(s));
  }

  public static Function<String, List<String>> wordSplitter(Languages.eLanguage language) {

    Preconditions.checkNotNull(language, "language should not be null");

    SnowballStemmer stemmer = Languages.stemmer(language);
    Set<String> stopwords = Languages.stopwords(language);

    return s -> Splitter.on(CharMatcher.whitespace().or(CharMatcher.breakingWhitespace()))
        .trimResults().omitEmptyStrings().splitToList(s).stream()
        .filter(word -> stopwords == null || !stopwords.contains(word)).map(word -> {
          stemmer.setCurrent(word);
          if (stemmer.stem()) {
            return stemmer.getCurrent();
          }
          return word;
        }).collect(Collectors.toList());
  }

  private static String normalize(String text) {

    Preconditions.checkNotNull(text, "text should not be null");

    // https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html
    // \p{Punct} -> Punctuation: One of !"#$%&'()*+,-./:;<=>?@[\]^_`{|}~
    return StringIterator.removeDiacriticalMarks(text).replaceAll("[^!?.,;:\\p{Alnum}\\p{L}]", " ")
        .replaceAll("\\s+", " ").toLowerCase();
  }
}
