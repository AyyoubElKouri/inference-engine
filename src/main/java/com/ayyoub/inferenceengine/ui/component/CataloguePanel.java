package com.ayyoub.inferenceengine.ui.component;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Left-hand catalogue of available fact templates.
 */
public final class CataloguePanel extends VBox {

  private final Map<String, CatalogueRow> rows = new HashMap<>();

  /**
   * Builds the panel and its rows.
   */
  public CataloguePanel(List<Fact> catalogue, Consumer<Fact> onAdd) {
    super(0);
    setStyle("-fx-background-color: " + DesignTokens.SURFACE + ";" + "-fx-border-color: transparent " + DesignTokens.BORDER
        + " transparent transparent;" + "-fx-border-width: 0 1 0 0;");

    VBox header = new VBox(4);
    header.setPadding(new Insets(24, 20, 16, 20));
    header.setStyle("-fx-border-color: transparent transparent " + DesignTokens.BORDER + " transparent;" + "-fx-border-width: 0 0 1 0;");

    Label title = new Label("Catalogue");
    title.setFont(Font.font("System", FontWeight.BOLD, 13));
    title.setTextFill(javafx.scene.paint.Color.web(DesignTokens.TXT1));

    header.getChildren().addAll(title, DesignTokens.label("Cliquez pour ajouter un fait", 11, DesignTokens.TXT3));

    VBox list = new VBox(4);
    list.setPadding(new Insets(12, 10, 12, 10));

    if (catalogue.isEmpty()) {
      Label empty = DesignTokens.label("Aucun attribut dans facts.json", 12, DesignTokens.TXT3);
      empty.setPadding(new Insets(8, 10, 8, 10));
      list.getChildren().add(empty);
    } else {
      for (Fact template : catalogue) {
        CatalogueRow row = new CatalogueRow(template, onAdd);
        rows.put(template.attribute(), row);
        list.getChildren().add(row);
      }
    }

    ScrollPane scroll = new ScrollPane(list);
    scroll.setFitToWidth(true);
    scroll.setBorder(javafx.scene.layout.Border.EMPTY);
    scroll.setStyle("-fx-background: " + DesignTokens.SURFACE + "; -fx-background-color: " + DesignTokens.SURFACE + ";");
    VBox.setVgrow(scroll, Priority.ALWAYS);

    getChildren().addAll(header, scroll);
  }

  /**
   * Marks one attribute as selected or available.
   */
  public void markUsed(String attribute, boolean used) {
    CatalogueRow row = rows.get(attribute);

    if (row != null)
      row.setUsed(used);
  }
}
