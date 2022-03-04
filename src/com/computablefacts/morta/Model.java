package com.computablefacts.morta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

import com.computablefacts.morta.snorkel.Dictionary;
import com.computablefacts.morta.snorkel.FeatureVector;
import com.computablefacts.morta.snorkel.Helpers;
import com.computablefacts.morta.snorkel.ITransformationFunction;
import com.computablefacts.morta.snorkel.classifiers.AbstractClassifier;
import com.computablefacts.morta.snorkel.labelingfunctions.AbstractLabelingFunction;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.CheckReturnValue;

@NotThreadSafe
@CheckReturnValue
final public class Model {

  private final String language_;
  private final String model_;
  private final int maxGroupSize_;
  private Dictionary alphabet_;
  private AbstractClassifier classifier_;
  private List<AbstractLabelingFunction<String>> labelingFunctions_;
  private double confidenceScore_;
  private String observations_;

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

  public List<AbstractLabelingFunction<String>> labelingFunctions() {
    return labelingFunctions_;
  }

  public List<String> keywords(String text) {
    return Helpers.keywords(labelingFunctions_, text);
  }

  public double confidenceScore() {
    return confidenceScore_;
  }

  public String observations() {
    return observations_;
  }

  public ITransformationFunction<String, FeatureVector<Double>> countVectorizer() {
    return Helpers.countVectorizer(alphabet_, maxGroupSize_);
  }

  public boolean isValid() {
    return !Strings.isNullOrEmpty(model_) && !Strings.isNullOrEmpty(language_) && maxGroupSize_ > 0
        && alphabet_ != null && classifier_ != null && labelingFunctions_ != null
        && 0.0 <= confidenceScore_ && confidenceScore_ <= 1.0;
  }

  public boolean init(String dir) {

    Preconditions.checkArgument(!Strings.isNullOrEmpty(dir),
        "dir should neither be null nor empty");

    alphabet_ = alphabet(dir);
    classifier_ = classifier(dir);
    labelingFunctions_ = labelingFunctions(dir);
    observations_ = observations(dir);

    if (classifier_ != null) {
      confidenceScore_ = classifier_.f1();
    }
    return isValid();
  }

  private Dictionary alphabet(String dir) {
    return Helpers.deserialize(Constants.alphabetGz(dir, language_, model_));
  }

  private AbstractClassifier classifier(String dir) {
    return Helpers.deserialize(Constants.classifierGz(dir, language_, model_));
  }

  private List<AbstractLabelingFunction<String>> labelingFunctions(String dir) {
    return Helpers.deserialize(Constants.labelingFunctionsGz(dir, language_, model_));
  }

  private String observations(String dir) {
    try {
      File file = new File(Constants.observations(dir));
      if (file.exists()) {
        return com.google.common.io.Files.asCharSource(file, StandardCharsets.UTF_8).read();
      }
    } catch (IOException e) {
      // TODO
    }
    return "";
  }
}
