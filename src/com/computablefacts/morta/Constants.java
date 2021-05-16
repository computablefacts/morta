package com.computablefacts.morta;

import java.io.File;

import com.google.errorprone.annotations.CheckReturnValue;

import smile.util.Strings;

@CheckReturnValue
final public class Constants {

  public static String observations(String dir) {
    return (Strings.isNullOrEmpty(dir) ? "" : dir + File.separator) + "observations.txt";
  }

  public static String newGoldLabelsJson(String dir, String language, String model) {
    return (Strings.isNullOrEmpty(dir) ? "" : dir + File.separator) + "new_gold_labels_for_" + model
        + "_" + language + ".json";
  }

  public static String labelModelXml(String dir, String language, String model) {
    return (Strings.isNullOrEmpty(dir) ? "" : dir + File.separator) + "label_model_for_" + model
        + "_" + language + ".xml";
  }

  public static String alphabetXml(String dir, String language, String model) {
    return (Strings.isNullOrEmpty(dir) ? "" : dir + File.separator) + "alphabet_for_" + model + "_"
        + language + ".xml";
  }

  public static String classifierXml(String dir, String language, String model) {
    return (Strings.isNullOrEmpty(dir) ? "" : dir + File.separator) + "classifier_for_" + model
        + "_" + language + ".xml";
  }

  public static String guesstimatedLabelingFunctionsXml(String dir, String language, String model) {
    return (Strings.isNullOrEmpty(dir) ? "" : dir + File.separator)
        + "guesstimated_labeling_functions_for_" + model + "_" + language + ".xml";
  }

  public static String labelingFunctionsXml(String dir, String language, String model) {
    return (Strings.isNullOrEmpty(dir) ? "" : dir + File.separator) + "labeling_functions_for_"
        + model + "_" + language + ".xml";
  }

  public static String newGoldLabelsGz(String dir, String language, String model) {
    return newGoldLabelsJson(dir, language, model) + ".gz";
  }

  public static String labelModelGz(String dir, String language, String model) {
    return labelModelXml(dir, language, model) + ".gz";
  }

  public static String alphabetGz(String dir, String language, String model) {
    return alphabetXml(dir, language, model) + ".gz";
  }

  public static String classifierGz(String dir, String language, String model) {
    return classifierXml(dir, language, model) + ".gz";
  }

  public static String guesstimatedLabelingFunctionsGz(String dir, String language, String model) {
    return guesstimatedLabelingFunctionsXml(dir, language, model) + ".gz";
  }

  public static String labelingFunctionsGz(String dir, String language, String model) {
    return labelingFunctionsXml(dir, language, model) + ".gz";
  }
}
