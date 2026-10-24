package com.ayyoub.inferenceengine.ui.component;

import java.util.function.Consumer;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * iOS-style boolean toggle.
 */
public final class BooleanInput implements Input {

  private static final double TRACK_WIDTH = 48;
  private static final double TRACK_HEIGHT = 26;
  private static final double KNOB_DIAMETER = 20;
  private static final double PADDING = 3;

  private final Consumer<Object> onChange;
  private final HBox row;
  private boolean value;

  /**
   * Builds the toggle.
   */
  public BooleanInput(boolean initial, Consumer<Object> onChange) {
    this.value = initial;
    this.onChange = onChange;

    Rectangle track = new Rectangle(TRACK_WIDTH, TRACK_HEIGHT);
    track.setArcWidth(TRACK_HEIGHT);
    track.setArcHeight(TRACK_HEIGHT);

    Circle knob = new Circle(KNOB_DIAMETER / 2);
    knob.setFill(Color.WHITE);
    knob.setEffect(new DropShadow(4, 0, 1, Color.rgb(0, 0, 0, 0.22)));

    StackPane switchPane = new StackPane(track, knob);
    switchPane.setPrefSize(TRACK_WIDTH, TRACK_HEIGHT);
    switchPane.setMaxSize(TRACK_WIDTH, TRACK_HEIGHT);
    switchPane.setStyle("-fx-cursor: hand;");

    applyState(track, knob, initial, false);

    switchPane.setOnMouseClicked(event -> {
      this.value = !this.value;
      onChange.accept(this.value);
      applyState(track, knob, this.value, true);
    });

    row = new HBox(10, DesignTokens.label("Non", 12, DesignTokens.TXT2), switchPane, DesignTokens.label("Oui", 12, DesignTokens.TXT2));
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new Insets(4, 0, 4, 0));
  }

  @Override
  public Node node() {
    return row;
  }

  @Override
  public Object value() {
    return value;
  }

  /**
   * Applies color and knob position, optionally animating.
   */
  private static void applyState(Rectangle track, Circle knob, boolean on, boolean animate) {
    double targetX = on ? (TRACK_WIDTH / 2) - (KNOB_DIAMETER / 2) - PADDING : -(TRACK_WIDTH / 2) + (KNOB_DIAMETER / 2) + PADDING;

    track.setFill(on ? Color.web(DesignTokens.PRIMARY) : Color.web(DesignTokens.BORDER_DARK));

    if (animate) {
      TranslateTransition transition = new TranslateTransition(Duration.millis(160), knob);
      transition.setToX(targetX);
      transition.play();
    } else {
      knob.setTranslateX(targetX);
    }
  }
}
