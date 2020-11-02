package com.computablefacts.morta.logs;

import com.computablefacts.logfmt.LogFormatter;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final public class LogFormatterManager {

  public LogFormatterManager() {}

  /**
   * Get a {@link LogFormatter} using the application "git.properties" file generated by Maven's
   * git-commit-id plugin.
   * 
   * @return {@link LogFormatter}
   */
  public static LogFormatter logFormatter() {
    return logFormatter("git-morta.properties");
  }

  /**
   * Get a {@link LogFormatter} using a user-defined property file.
   *
   * @param gitProperties name of the property file file as generated by Maven's git-commit-id
   *        plugin.
   * @return {@link LogFormatter}
   */
  public static LogFormatter logFormatter(String gitProperties) {
    if (!Strings.isNullOrEmpty(gitProperties)) {
      return LogFormatter.create().addGitProperties(gitProperties);
    }
    return LogFormatter.create();
  }
}
