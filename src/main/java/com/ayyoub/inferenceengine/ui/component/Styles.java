package com.ayyoub.inferenceengine.ui.component;

/**
 * Shared JavaFX style strings for form controls.
 */
public final class Styles {

  /**
   * Text field, spinner, and decimal field.
   */
  public static String input() {
    return "-fx-background-color: " + DesignTokens.BG + ";" + "-fx-border-color: " + DesignTokens.BORDER_DARK + ";"
        + "-fx-border-radius: 7; -fx-background-radius: 7;" + "-fx-padding: 7 11; -fx-font-size: 13;";
  }

  /**
   * Combo box.
   */
  public static String combo() {
    return "-fx-background-color: " + DesignTokens.BG + ";" + "-fx-border-color: " + DesignTokens.BORDER_DARK + ";"
        + "-fx-border-radius: 7; -fx-background-radius: 7;" + "-fx-padding: 3 6; -fx-font-size: 13;" + "-fx-cursor: hand;";
  }

  private Styles() {
  }
}
