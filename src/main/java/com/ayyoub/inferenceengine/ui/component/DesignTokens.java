package com.ayyoub.inferenceengine.ui.component;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Single source of truth for the UI's colors, sizes, and shared helpers.
 */
public final class DesignTokens {

  public static final String BG = "#F7F8FA";
  public static final String SURFACE = "#FFFFFF";
  public static final String PRIMARY = "#1B4FD8";
  public static final String PRIMARY_ALT = "#1640C0";
  public static final String PRIMARY_TL = "#EEF2FF";
  public static final String BORDER = "#E5E7EB";
  public static final String BORDER_DARK = "#D1D5DB";
  public static final String TXT1 = "#0D0D14";
  public static final String TXT2 = "#6B7280";
  public static final String TXT3 = "#9CA3AF";
  public static final String SUCCESS = "#059669";
  public static final String WARN = "#D97706";
  public static final String DANGER = "#DC2626";
  public static final String DANGER_TL = "#FEF2F2";
  public static final String RULE = "#7C3AED";
  public static final String RULE_TL = "#F5F3FF";

  /**
   * Shared small label factory.
   */
  public static Label label(String text, int size, String color) {
    Label label = new Label(text);
    label.setFont(Font.font("System", size));
    label.setTextFill(Color.web(color));
    return label;
  }

  /**
   * Fixed-width horizontal spacer.
   */
  public static Region spacer(double width) {
    Region region = new Region();
    region.setPrefWidth(width);
    return region;
  }

  private DesignTokens() {
  }
}
