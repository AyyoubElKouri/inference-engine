package com.ayyoub.inferenceengine.domain;

/**
 * Witness that a negated branch holds because its leaf is absent from working memory.
 */
public record AbsenceWitness(
    String attribute,
    String operator,
    Object value) {

  /**
   * Short badge text for the trace graph, such as "C absent".
   */
  public String label() {
    return attribute + " absent";
  }
}
