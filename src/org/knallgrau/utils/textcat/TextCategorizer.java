package org.knallgrau.utils.textcat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import com.computablefacts.asterix.Generated;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;

@Generated
@CheckReturnValue
final public class TextCategorizer {

  private final ArrayList<FingerPrint> categories = new ArrayList<>();
  private File confFile = null;

  public TextCategorizer() {}

  public void add(FingerPrint fingerPrint) {
    categories.add(fingerPrint);
  }

  public void setConfFile(String confFile) {
    this.confFile = confFile == null ? null : new File(confFile);
    this.loadCategories();
  }

  private void loadCategories() {

    this.categories.clear();

    try {

      MyProperties properties = new MyProperties();

      if (this.confFile == null) {
        properties.load(TextCategorizer.class.getClassLoader()
            .getResourceAsStream("org/knallgrau/utils/textcat/textcat.conf"));
      } else {
        properties.load(new FileInputStream(this.confFile.toString()));
      }

      Iterator var3 = properties.entrySet().iterator();

      while (var3.hasNext()) {

        Map.Entry<String, String> entry = (Map.Entry) var3.next();
        FingerPrint fp;

        if (this.confFile == null) {
          fp = new FingerPrint(
              TextCategorizer.class.getClassLoader().getResourceAsStream(entry.getKey()));
        } else {
          fp = new FingerPrint(entry.getKey());
        }

        fp.setCategory(entry.getValue());
        this.categories.add(fp);
      }
    } catch (FileNotFoundException var5) {
      var5.printStackTrace();
    }
  }

  public String categorize(String text) {
    if (text.length() < 10) {
      return "unknown";
    } else {
      FingerPrint fp = new FingerPrint();
      fp.create(text);
      fp.categorize(this.categories);
      return fp.getCategory();
    }
  }

  public String categorize(String text, int limit) {
    return limit > text.length() - 1 ? this.categorize(text)
        : this.categorize(text.substring(0, limit));
  }

  public Map<String, Integer> getCategoryDistances(String text) {

    Preconditions.checkState(!this.categories.isEmpty());

    FingerPrint fp = new FingerPrint();
    fp.create(text);
    fp.categorize(this.categories);

    return fp.getCategoryDistances();
  }
}
