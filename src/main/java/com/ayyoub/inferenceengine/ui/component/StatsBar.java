package com.ayyoub.inferenceengine.ui.component;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Summary counts above the trace graph.
 */
public final class StatsBar extends HBox {

  /**
   * Builds the bar from one run's facts.
   */
  public StatsBar(List<Fact> facts) {
    super(0);
    setStyle("-fx-background-color: " + DesignTokens.SURFACE + "; -fx-border-color: " + DesignTokens.BORDER + ";"
        + "-fx-border-radius: 12; -fx-background-radius: 12;");
    setAlignment(Pos.CENTER);

    long derived = facts.stream().filter(fact -> fact.derivedBy() != null).count();
    long initial = facts.size() - derived;
    long levels = facts.stream().mapToInt(Fact::level).distinct().count();
    boolean alert = facts.stream().anyMatch(fact -> fact.attribute().startsWith("alerte") || fact.attribute().equals("conseil"));

    getChildren().addAll(statCell(String.valueOf(facts.size()), "Faits totaux", DesignTokens.PRIMARY), divider(),
        statCell(String.valueOf(initial), "Initiaux", DesignTokens.TXT2), divider(),
        statCell(String.valueOf(derived), "Déduits", DesignTokens.SUCCESS), divider(), statCell(String.valueOf(levels), "Niveaux", DesignTokens.RULE),
        divider(), statCell(alert ? "Oui" : "Non", "Alerte", alert ? DesignTokens.WARN : DesignTokens.TXT3));
  }

  private static VBox statCell(String value, String label, String color) {
    VBox cell = new VBox(2);
    cell.setAlignment(Pos.CENTER);
    cell.setPadding(new Insets(10, 32, 10, 32));
    HBox.setHgrow(cell, Priority.ALWAYS);

    Label number = new Label(value);
    number.setFont(Font.font("System", FontWeight.BOLD, 19));
    number.setTextFill(Color.web(color));

    Label caption = new Label(label);
    caption.setFont(Font.font("System", 11));
    caption.setTextFill(Color.web(DesignTokens.TXT3));

    cell.getChildren().addAll(number, caption);
    return cell;
  }

  private static Region divider() {
    Region divider = new Region();
    divider.setPrefWidth(1);
    divider.setMinHeight(36);
    divider.setStyle("-fx-background-color: " + DesignTokens.BORDER + ";");
    return divider;
  }
}
