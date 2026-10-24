package com.ayyoub.inferenceengine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@lombok.Data
@JsonIgnoreProperties(ignoreUnknown = true) // ← ignore tous les champs JSON inconnus
public class Rule {

  /**
   * ID of the rule.
   */
  @JsonProperty("id")
  private String ruleId;

  /**
   * Name of the rule.
   */
  private String name;

  /**
   * Conditions of the rule.
   */
  private Map<String, Object> condition;

  /**
   * Conclusions of the rule.
   */
  private List<Map<String, Object>> conclusions;

  /**
   * Priority of the rule.
   */
  private int priority;

  /**
   * Confidence level (0.0 – 1.0).
   */
  private double confidence;

  /**
   * Human-readable explanation.
   */
  private String explanation;
}
