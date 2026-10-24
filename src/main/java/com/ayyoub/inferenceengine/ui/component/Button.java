package com.ayyoub.inferenceengine.ui.component;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Styled button with a small set of design-system variants.
 */
public class Button extends javafx.scene.control.Button {

  /**
   * Supported visual variants.
   */
  public enum Variant {
    PRIMARY, SECONDARY, GHOST, DANGER
  }

  private final Variant variant;

  /**
   * Creates a button in the requested variant.
   */
  public Button(String text, Variant variant) {
    super(text);
    this.variant = variant;
    setFont(Font.font("System", FontWeight.BOLD, 12));
    setStyle(style(false));
    setOnMouseEntered(event -> {
      if (!isDisabled())
        setStyle(style(true));
    });
    setOnMouseExited(event -> setStyle(style(false)));
    disabledProperty().addListener((observable, previous, current) -> setStyle(style(false)));
  }

  /**
   * Style string for the current variant and hover state.
   */
  private String style(boolean hover) {
    return switch (variant) {
      case PRIMARY -> "-fx-background-color: " + (hover ? DesignTokens.PRIMARY_ALT : DesignTokens.PRIMARY) + ";"
          + "-fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 9 22; -fx-cursor: hand;";
      case SECONDARY -> "-fx-background-color: " + (hover ? DesignTokens.PRIMARY_TL : DesignTokens.SURFACE) + ";" + "-fx-text-fill: "
          + DesignTokens.TXT1 + "; -fx-border-color: " + DesignTokens.BORDER + ";"
          + "-fx-border-radius: 7; -fx-background-radius: 7; -fx-padding: 6 14; -fx-cursor: hand;";
      case GHOST -> "-fx-background-color: transparent; -fx-text-fill: " + (hover ? DesignTokens.TXT1 : DesignTokens.TXT3) + ";"
          + "-fx-padding: 2 6; -fx-cursor: hand; -fx-background-radius: 5;";
      case DANGER -> "-fx-background-color: " + (hover ? DesignTokens.DANGER_TL : "transparent") + ";" + "-fx-text-fill: "
          + (hover ? DesignTokens.DANGER : DesignTokens.TXT3) + ";" + "-fx-padding: 2 6; -fx-cursor: hand; -fx-background-radius: 5;";
    };
  }
}
