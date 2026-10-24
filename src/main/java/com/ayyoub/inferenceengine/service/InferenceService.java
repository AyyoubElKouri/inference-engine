package com.ayyoub.inferenceengine.service;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.List;

/**
 * Use-case boundary between the UI and the reasoning engine.
 */
public interface InferenceService {

  /**
   * Templates available in the fact builder.
   */
  List<Fact> factCatalogue();

  /**
   * Known string values for an attribute, in encounter order.
   */
  List<String> valueOptions(String attribute);

  /**
   * Runs one inference session over the supplied facts.
   */
  InferenceResult run(List<FactInput> inputs);
}
