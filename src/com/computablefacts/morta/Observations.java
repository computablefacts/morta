package com.computablefacts.morta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.concurrent.NotThreadSafe;

import com.computablefacts.asterix.View;
import com.google.common.base.Stopwatch;
import com.google.errorprone.annotations.CheckReturnValue;

@NotThreadSafe
@CheckReturnValue
final public class Observations {

  private final Stopwatch stopwatch_ = Stopwatch.createStarted();
  private final List<String> observations_ = new ArrayList<>();
  private final File file_;

  public Observations(File file) {
    file_ = file;
  }

  public void flush() {

    stopwatch_.stop();
    observations_.add(String.format("Elapsed time : %ds", stopwatch_.elapsed(TimeUnit.SECONDS)));

    if (file_ != null) {
      View.of(observations_).toFile(Function.identity(), file_, file_.exists());
      observations_.clear();
    }

    stopwatch_.start();
  }

  public void add(String message) {
    if (message != null) {

      String msg = message.trim();

      if (file_ != null) {
        observations_.add(msg);
      }
      System.out.println(msg);
    }
    autoFlush();
  }

  private void autoFlush() {
    if (file_ != null) {
      if (observations_.size() >= 50) {
        View.of(observations_).toFile(Function.identity(), file_, file_.exists());
        observations_.clear();
      }
    }
  }
}
