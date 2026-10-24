package com.ayyoub.inferenceengine.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Page header with app name, breadcrumb, and an optional back button.
 */
public final class TopBar extends HBox {

  /**
   * Builds the bar.
   */
  public TopBar(boolean resultsMode, Runnable onBack) {
    super(14);
    setPadding(new Insets(17, 40, 17, 40));
    setAlignment(Pos.CENTER_LEFT);
    setStyle("-fx-background-color: " + DesignTokens.SURFACE + ";" + "-fx-border-color: transparent transparent " + DesignTokens.BORDER
        + " transparent;" + "-fx-border-width: 0 0 1 0;");

    if (resultsMode) {
      Button back = new Button("← Retour", Button.Variant.SECONDARY);
      back.setOnAction(event -> onBack.run());
      getChildren().addAll(back, DesignTokens.spacer(16));
    }

    Circle dot = new Circle(5, Color.web(DesignTokens.PRIMARY));

    Label appName = new Label("Moteur d'Inférence");
    appName.setFont(Font.font("System", FontWeight.BOLD, 15));
    appName.setTextFill(Color.web(DesignTokens.TXT1));

    Label breadcrumb = DesignTokens.label(resultsMode ? "/ Trace de raisonnement" : "/ Configuration des faits", 14, DesignTokens.TXT3);

    getChildren().addAll(dot, appName, breadcrumb);
  }
}
