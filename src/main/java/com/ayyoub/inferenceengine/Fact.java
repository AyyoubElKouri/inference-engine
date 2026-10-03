package com.ayyoub.inferenceengine;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@lombok.Data
public class Fact {
  /** ID of the fact. */
  private String factId;

  /** The attribute of the fact (e.g., "temperature", "humidity"). */
  private String attribute;

  /** The operator of the fact (e.g., "=", "!=", ">", "<"). */
  private String operator;

  /** The value of the fact. */
  private Object value;

  /** The confidence level of the fact. */
  private double confidence;

  /** The rule that derived this fact. */
  private String derivedBy;

  /**
   * The level of the fact in the inference process (e.g., 0 for initial facts, 1 for facts derived
   * from level 0, etc.).
   */
  private int level;
}
