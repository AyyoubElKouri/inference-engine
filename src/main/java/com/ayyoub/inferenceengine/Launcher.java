package com.ayyoub.inferenceengine;

import com.ayyoub.inferenceengine.ui.EngineApplication;
import javafx.application.Application;

/**
 * Plain entry point that boots the JavaFX application.
 *
 * <p>
 * This class intentionally does not extend {@link Application}: keeping the JAR's main class free of JavaFX inheritance lets the shaded JAR start
 * without the JavaFX runtime being detected as missing. It is the documented exception to the rule that JavaFX code lives under {@code ui}.
 */
public final class Launcher {

  /**
   * Starts the JavaFX application.
   */
  public static void main(String[] args) {
    Application.launch(EngineApplication.class, args);
  }
}
