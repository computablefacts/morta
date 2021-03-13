package com.computablefacts.morta;

import java.io.File;

import smile.util.Strings;

final public class Constants {

  public static String observations(String dir) {
    return (Strings.isNullOrEmpty(dir) ? "" : dir + File.separator) + "observations.txt";
  }

  public static String countsXml(String dir, String language, String model) {
    return (Strings.isNullOrEmpty(dir) ? "" : dir + File.separator) + "counts_for_" + model + "_"
        + language + ".xml";
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

  public static String labelingFunctionsXml(String dir, String language, String model) {
    return (Strings.isNullOrEmpty(dir) ? "" : dir + File.separator) + "labeling_functions_for_"
        + model + "_" + language + ".xml";
  }

  public static String labelModelGz(String dir, String language, String model) {
    return labelModelXml(dir, language, model) + ".gz";
  }

  public static String countsGz(String dir, String language, String model) {
    return countsXml(dir, language, model) + ".gz";
  }

  public static String alphabetGz(String dir, String language, String model) {
    return alphabetXml(dir, language, model) + ".gz";
  }

  public static String classifierGz(String dir, String language, String model) {
    return classifierXml(dir, language, model) + ".gz";
  }

  public static String labelingFunctionsGz(String dir, String language, String model) {
    return labelingFunctionsXml(dir, language, model) + ".gz";
  }
}
