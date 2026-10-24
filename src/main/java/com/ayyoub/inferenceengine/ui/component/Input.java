package com.ayyoub.inferenceengine.ui.component;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.Node;

/**
 * A typed editor for one fact template.
 */
public interface Input {

  /**
   * Rendered control.
   */
  Node node();

  /**
   * Current typed value.
   */
  Object value();

  /**
   * Picks the editor that matches the template's value type.
   */
  static Input forFact(Fact template, List<String> options, Consumer<Object> onChange) {
    Object value = template.value();

    if (value instanceof Boolean flag)
      return new BooleanInput(flag, onChange);
    if (value instanceof Integer number)
      return new IntegerInput(number, onChange);
    if (value instanceof Double number)
      return new DecimalInput(number, onChange);
    if (options.size() > 1)
      return new ChoiceInput(value != null ? value.toString() : "", options, onChange);

    return new TextInput(value != null ? value.toString() : "", onChange);
  }
}
