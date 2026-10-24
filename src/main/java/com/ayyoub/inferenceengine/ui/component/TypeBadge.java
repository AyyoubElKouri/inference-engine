package com.ayyoub.inferenceengine.ui.component;

import javafx.scene.control.Label;
import javafx.scene.text.Font;

/**
 * Small badge naming a value's type.
 */
public final class TypeBadge {

  /**
   * Builds the badge for one value.
   */
  public static Label of(Object value) {
    String text;
    String foreground;
    String background;

    if (value instanceof Boolean) {
      text = "booléen";
      foreground = DesignTokens.RULE;
      background = DesignTokens.RULE_TL;
    } else if (value instanceof Integer) {
      text = "entier";
      foreground = "#0891B2";
      background = "#ECFEFF";
    } else if (value instanceof Double) {
      text = "décimal";
      foreground = "#0891B2";
      background = "#ECFEFF";
    } else {
      text = "texte";
      foreground = DesignTokens.TXT2;
      background = DesignTokens.BG;
    }

    Label badge = new Label(text);
    badge.setFont(Font.font("System", 10));
    badge.setTextFill(javafx.scene.paint.Color.web(foreground));
    badge.setStyle("-fx-background-color: " + background + "; -fx-background-radius: 20; -fx-padding: 2 9;");
    return badge;
  }

  private TypeBadge() {
  }
}
