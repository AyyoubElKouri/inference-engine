package com.ayyoub.inferenceengine.ui.component;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * One selected fact card: attribute, badge, remove button, and typed input.
 */
public final class FactRow extends HBox {

  /**
   * Builds the card.
   */
  public FactRow(Fact template, List<String> options, Consumer<Object> onValueChange, Runnable onRemove) {
    super(0);
    setStyle("-fx-background-color: " + DesignTokens.SURFACE + ";" + "-fx-border-color: " + DesignTokens.BORDER + ";"
        + "-fx-border-radius: 10; -fx-background-radius: 10;");

    Region strip = new Region();
    strip.setPrefWidth(4);
    strip.setMinWidth(4);
    strip.setStyle("-fx-background-color: " + DesignTokens.PRIMARY + "; -fx-background-radius: 10 0 0 10;");

    VBox body = new VBox(10);
    body.setPadding(new Insets(14, 14, 14, 12));
    HBox.setHgrow(body, Priority.ALWAYS);

    HBox header = new HBox(8);
    header.setAlignment(Pos.CENTER_LEFT);

    Label attribute = new Label(template.attribute());
    attribute.setFont(Font.font("System", FontWeight.BOLD, 13));
    attribute.setTextFill(javafx.scene.paint.Color.web(DesignTokens.TXT1));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Button remove = new Button("✕", Button.Variant.DANGER);
    remove.setOnAction(event -> onRemove.run());

    header.getChildren().addAll(attribute, TypeBadge.of(template.value()), spacer, remove);

    Input input = Input.forFact(template, options, onValueChange);
    body.getChildren().addAll(header, input.node());
    getChildren().addAll(strip, body);
  }
}
