package com.ayyoub.inferenceengine.ui.component;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * One clickable catalogue entry.
 */
public final class CatalogueRow extends HBox {

  private final Fact template;
  private boolean used;

  /**
   * Builds the row.
   */
  public CatalogueRow(Fact template, Consumer<Fact> onAdd) {
    super(10);
    this.template = template;
    setPadding(new Insets(9, 12, 9, 12));
    setAlignment(Pos.CENTER_LEFT);
    setStyle(idleStyle());

    Label attribute = new Label(template.attribute());
    attribute.setFont(Font.font("System", FontWeight.BOLD, 12));
    attribute.setTextFill(javafx.scene.paint.Color.web(DesignTokens.TXT1));
    HBox.setHgrow(attribute, Priority.ALWAYS);

    Label addIcon = new Label("+");
    addIcon.setFont(Font.font("System", FontWeight.BOLD, 14));
    addIcon.setTextFill(javafx.scene.paint.Color.web(DesignTokens.PRIMARY));
    addIcon.setMinWidth(20);
    addIcon.setAlignment(Pos.CENTER);
    addIcon.setVisible(false);

    getChildren().addAll(attribute, TypeBadge.of(template.value()), addIcon);

    setOnMouseEntered(event -> {
      if (used)
        return;
      setStyle("-fx-background-color: " + DesignTokens.PRIMARY_TL + ";" + "-fx-background-radius: 8; -fx-cursor: hand;");
      addIcon.setVisible(true);
    });
    setOnMouseExited(event -> {
      if (used)
        return;
      setStyle(idleStyle());
      addIcon.setVisible(false);
    });
    setOnMouseClicked(event -> {
      if (!used)
        onAdd.accept(template);
    });
  }

  /**
   * Dims and disables the row while its attribute is selected.
   */
  public void setUsed(boolean used) {
    this.used = used;
    setMouseTransparent(used);
    setOpacity(used ? 0.4 : 1.0);
    setStyle(used ? idleStyle() + "-fx-cursor: default;" : idleStyle());
  }

  private static String idleStyle() {
    return "-fx-background-color: transparent;" + "-fx-background-radius: 8;" + "-fx-cursor: hand;";
  }
}
