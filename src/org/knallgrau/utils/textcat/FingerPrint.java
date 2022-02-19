package org.knallgrau.utils.textcat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.computablefacts.asterix.Generated;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Var;

@Generated
@CheckReturnValue
public class FingerPrint extends Hashtable<String, Integer> {

  private final Pattern pattern = Pattern.compile("^_?[^0-9\\?!\\-_/]*_?$");
  private final HashMap<String, Integer> categoryDistances = new HashMap<>();
  private String category = "unknown";
  private TreeSet<Map.Entry<String, Integer>> entries;

  public FingerPrint() {}

  public FingerPrint(String file) {
    this.loadFingerPrintFromFile(file);
  }

  public FingerPrint(InputStream is) {
    this.loadFingerPrintFromInputStream(is);
  }

  public void create(String text) {

    this.clear();
    this.computeNGrams(1, 5, text);

    if (this.containsKey("_")) {
      int blanksScore = this.remove("_");
      this.put("_", blanksScore / 2);
    }

    this.entries = new TreeSet<>(new NGramEntryComparator());
    this.entries.addAll(this.entrySet());
  }

  private void computeNGrams(int startOrder, int maxOrder, String text) {

    String[] tokens = text.split("\\s");

    for (int order = startOrder; order <= maxOrder; ++order) {

      String[] var9 = tokens;
      @Var
      int var7 = 0;

      for (int var8 = tokens.length; var7 < var8; ++var7) {

        @Var
        String token = var9[var7];
        token = "_" + token + "_";

        for (int i = 0; i < token.length() - order + 1; ++i) {

          String ngram = token.substring(i, i + order);
          Matcher matcher = this.pattern.matcher(ngram);

          if (matcher.find()) {
            if (!this.containsKey(ngram)) {
              this.put(ngram, 1);
            } else {
              @Var
              int score = this.remove(ngram);
              ++score;
              this.put(ngram, score);
            }
          }
        }
      }
    }
  }

  private void loadFingerPrintFromFile(String file) {

    File fpFile = new File(file);

    if (!fpFile.isDirectory()) {
      try {
        FileInputStream fis = new FileInputStream(file);
        this.loadFingerPrintFromInputStream(fis);
      } catch (FileNotFoundException var4) {
        var4.printStackTrace();
      }
    }
  }

  private void loadFingerPrintFromInputStream(InputStream is) {

    this.entries = new TreeSet(new NGramEntryComparator());
    MyProperties properties = new MyProperties();
    properties.load(is);

    Iterator var4 = properties.entrySet().iterator();

    while (var4.hasNext()) {
      Map.Entry<String, String> entry = (Map.Entry) var4.next();
      this.put(entry.getKey(), Integer.parseInt(entry.getValue()));
    }
    this.entries.addAll(this.entrySet());
  }

  @CanIgnoreReturnValue
  public Map<String, Integer> categorize(Collection<FingerPrint> categories) {

    @Var
    int minDistance = 2147483647;
    Iterator var4 = categories.iterator();

    while (var4.hasNext()) {

      FingerPrint fp = (FingerPrint) var4.next();
      int distance = this.getDistance(fp);
      this.getCategoryDistances().put(fp.getCategory(), distance);

      if (distance < minDistance) {
        minDistance = distance;
        this.category = fp.getCategory();
      }
    }
    return this.getCategoryDistances();
  }

  public Map<String, Integer> getCategoryDistances() {
    return this.categoryDistances;
  }

  private int getDistance(FingerPrint category) {

    @Var
    int distance = 0;
    @Var
    int count = 0;
    Iterator var5 = this.entries.iterator();

    while (var5.hasNext()) {

      Map.Entry<String, Integer> entry = (Map.Entry) var5.next();
      String ngram = entry.getKey();
      ++count;

      if (count > 400) {
        break;
      }
      if (!category.containsKey(ngram)) {
        distance += category.size();
      } else {
        distance += Math.abs(this.getPosition(ngram) - category.getPosition(ngram));
      }
    }
    return distance;
  }

  public int getPosition(String key) {

    @Var
    int pos = 1;
    @Var
    int value = (Integer) ((Map.Entry) this.entries.first()).getValue();
    Iterator var5 = this.entries.iterator();
    @Var
    Map.Entry entry;

    do {
      if (!var5.hasNext()) {
        return -1;
      }

      entry = (Map.Entry) var5.next();

      if (value != (Integer) entry.getValue()) {
        value = (Integer) entry.getValue();
        ++pos;
      }
    } while (!entry.getKey().equals(key));

    return pos;
  }

  public String getCategory() {
    return this.category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  @Override
  public String toString() {

    @Var
    String s = "";
    @Var
    Map.Entry entry;

    for (Iterator var3 = this.entries.iterator(); var3.hasNext(); s =
        s + entry.getKey() + "\t" + entry.getValue() + "\n") {
      entry = (Map.Entry) var3.next();
    }
    return s;
  }
}
