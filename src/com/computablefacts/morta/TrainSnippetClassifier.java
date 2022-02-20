package com.computablefacts.morta;

import static com.computablefacts.morta.snorkel.ILabelingFunction.KO;
import static com.computablefacts.morta.snorkel.ILabelingFunction.OK;

import java.io.File;

import com.computablefacts.asterix.ConfusionMatrix;
import com.computablefacts.asterix.View;
import com.computablefacts.asterix.codecs.JsonCodec;
import com.computablefacts.asterix.console.AsciiProgressBar;
import com.computablefacts.asterix.console.ConsoleApp;
import com.computablefacts.morta.snorkel.GoldLabel;
import com.computablefacts.morta.snorkel.IGoldLabel;
import com.computablefacts.morta.snorkel.labelmodels.TreeLabelModel;
import com.computablefacts.morta.textcat.FingerPrint;
import com.computablefacts.morta.textcat.TextCategorizer;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class TrainSnippetClassifier extends ConsoleApp {

  public static void main(String[] args) {

    Preconditions.checkNotNull(args, "args should not be null");

    File goldLabels = getFileCommand(args, "gold_labels", null);
    String outputOk = getStringCommand(args, "output_ok", null);
    String outputKo = getStringCommand(args, "output_ko", null);
    String outputConf = getStringCommand(args, "output_conf", null);
    String label = getStringCommand(args, "label", null);

    StringBuilder builderOk = new StringBuilder();
    StringBuilder builderKo = new StringBuilder();

    AsciiProgressBar.IndeterminateProgressBar bar = AsciiProgressBar.createIndeterminate();

    double avgLength = View.of(goldLabels, true).index()
        .filter(e -> !Strings.isNullOrEmpty(e.getValue())).peek(e -> bar.update())
        .map(e -> (IGoldLabel<String>) new GoldLabel(JsonCodec.asObject(e.getValue())))
        .filter(gl -> !Strings.isNullOrEmpty(gl.snippet())).filter(gl -> label.equals(gl.label()))
        .peek(gl -> {
          if (TreeLabelModel.label(gl) == OK) {
            builderOk.append(gl.snippet()).append("\n\n\n");
          } else {
            builderKo.append(gl.snippet()).append("\n\n\n");
          }
        }).map(gl -> gl.snippet().length()).toList().stream().mapToInt(i -> i).average().orElse(0);

    bar.complete();

    System.out.println(); // Cosmetic
    System.out.println("avg snippet length : " + avgLength);

    FingerPrint fpOk = new FingerPrint();
    fpOk.category("OK");
    fpOk.create(builderOk.toString());

    FingerPrint fpKo = new FingerPrint();
    fpKo.category("KO");
    fpKo.create(builderKo.toString());

    testFingerprints(goldLabels, label, fpOk, fpKo);
  }

  private static void testFingerprints(File goldLabels, String label, FingerPrint fpOk,
      FingerPrint fpKo) {

    Preconditions.checkNotNull(goldLabels, "goldLabels should not be null");
    Preconditions.checkNotNull(fpOk, "fpOk should not be null");
    Preconditions.checkNotNull(fpKo, "fpKo should not be null");
    Preconditions.checkNotNull(label, "label should not be null");

    TextCategorizer guesser = new TextCategorizer();
    guesser.add(fpOk);
    guesser.add(fpKo);

    ConfusionMatrix matrix = new ConfusionMatrix();

    int nbUnknownCategories =
        View.of(goldLabels, true).index().filter(e -> !Strings.isNullOrEmpty(e.getValue()))
            .map(e -> (IGoldLabel<String>) new GoldLabel(JsonCodec.asObject(e.getValue())))
            .filter(gl -> !Strings.isNullOrEmpty(gl.snippet()))
            .filter(gl -> label.equals(gl.label())).map(gl -> {

              String category = guesser.categorize(gl.snippet());

              if ("unknown".equals(category)) {
                System.out.println("unknown_category -> " + gl.snippet());
                return 1;
              }
              if (TreeLabelModel.label(gl) == OK && category.endsWith("OK")) {
                matrix.addTruePositives(1);
              } else if (TreeLabelModel.label(gl) == KO && category.endsWith("KO")) {
                matrix.addTrueNegatives(1);
              } else if (TreeLabelModel.label(gl) == OK && category.endsWith("KO")) {
                matrix.addFalseNegatives(1);
              } else {
                matrix.addFalsePositives(1);
              }
              return 0;
            }).reduce(0, Integer::sum);

    System.out.println("# unknown categories : " + nbUnknownCategories);
    System.out.println(matrix);
  }
}
