package com.ayyoub.inferenceengine.ui.component;

import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.TextField;

/**
 * Decimal text field that keeps the last valid value.
 */
public final class DecimalInput implements Input {

  private final TextField field;
  private double value;

  /**
   * Builds the field.
   */
  public DecimalInput(double initial, Consumer<Object> onChange) {
    this.value = initial;
    field = new TextField(String.valueOf(initial));
    field.setStyle(Styles.input());
    field.textProperty().addListener((observable, previous, current) -> {
      try {
        this.value = Double.parseDouble(current);
        onChange.accept(this.value);
      } catch (NumberFormatException ignored) {
        // Keep the last valid value while the user is typing.
      }
    });
  }

  @Override
  public Node node() {
    return field;
  }

  @Override
  public Object value() {
    return value;
  }
}
