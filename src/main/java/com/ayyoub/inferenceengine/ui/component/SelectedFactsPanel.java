package com.ayyoub.inferenceengine.ui.component;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Right-hand list of selected facts.
 */
public final class SelectedFactsPanel extends VBox {

  private final VBox list = new VBox(8);
  private final Label hint;

  /**
   * Builds the panel.
   */
  public SelectedFactsPanel() {
    super(0);
    setStyle("-fx-background-color: " + DesignTokens.BG + ";");

    VBox header = new VBox(4);
    header.setPadding(new Insets(24, 24, 16, 24));
    header.setStyle("-fx-background-color: " + DesignTokens.BG + ";" + "-fx-border-color: transparent transparent " + DesignTokens.BORDER
        + " transparent;" + "-fx-border-width: 0 0 1 0;");

    Label title = new Label("Faits sélectionnés");
    title.setFont(Font.font("System", FontWeight.BOLD, 13));
    title.setTextFill(javafx.scene.paint.Color.web(DesignTokens.TXT1));

    hint = DesignTokens.label("Aucun fait ajouté — sélectionnez dans le catalogue", 11, DesignTokens.TXT3);
    header.getChildren().addAll(title, hint);

    list.setPadding(new Insets(16, 24, 16, 24));

    ScrollPane scroll = new ScrollPane(list);
    scroll.setFitToWidth(true);
    scroll.setBorder(javafx.scene.layout.Border.EMPTY);
    scroll.setStyle("-fx-background: " + DesignTokens.BG + "; -fx-background-color: " + DesignTokens.BG + ";");
    VBox.setVgrow(scroll, Priority.ALWAYS);

    getChildren().addAll(header, scroll);
  }

  /**
   * Appends one fact card.
   */
  public void addRow(Node row) {
    list.getChildren().add(row);
  }

  /**
   * Removes one fact card.
   */
  public void removeRow(Node row) {
    list.getChildren().remove(row);
  }

  /**
   * Updates the count hint.
   */
  public void setCount(int count) {
    hint.setText(count == 0
        ? "Aucun fait ajouté — sélectionnez dans le catalogue"
        : count + " fait" + (count > 1 ? "s" : "") + " sélectionné" + (count > 1 ? "s" : ""));
  }
}
