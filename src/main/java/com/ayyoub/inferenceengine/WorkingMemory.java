package com.ayyoub.inferenceengine;

import java.util.ArrayList;
import java.util.List;

public class WorkingMemory {
  /** List of facts currently in the working memory. */
  private List<Fact> facts;

  /** Constructor */
  public WorkingMemory() {
    this.facts = new ArrayList<>();
  }

  /**
   * Add a fact to the working memory.
   *
   * @param fact
   *          The fact to be added.
   */
  public void addFact(Fact fact) {
    facts.add(fact);
  }

  /**
   * Get the list of facts in the working memory.
   *
   * @return The list of facts.
   */
  public List<Fact> getFacts() {
    return facts;
  }

  /**
   * Check if a fact with the given attribute, operator, and value exists in the
   * working memory.
   *
   * @param attribute
   *          The attribute to check.
   * @param operator
   *          The operator to check (e.g., "=", "!=", ">", "<").
   * @param value
   *          The value to check.
   * @return True if a matching fact exists, false otherwise.
   */
  public boolean factExists(String attribute, String operator, Object value) {
    // Search for a fact that matches the given attribute, operator, and value
    return facts.stream().anyMatch(fact -> fact.getAttribute().equals(attribute) && compare(fact.getValue(), operator, value));
  }

  /**
   * Compare two values based on the given operator.
   *
   * @param real
   *          The actual value from the fact.
   * @param operator
   *          The operator to compare (e.g., "=", "!=", ">", "<").
   * @param expected
   *          The expected value to compare against.
   * @return True if the comparison holds, false otherwise.
   */
  @SuppressWarnings("unchecked")
  private boolean compare(Object real, String operator, Object expected) {
    if (real == null || expected == null)
      return false;

    return switch (operator) {
      case "=" -> real.equals(expected);
      case "!=" -> !real.equals(expected);
      case ">" -> ((Comparable<Object>) real).compareTo(expected) > 0;
      case "<" -> ((Comparable<Object>) real).compareTo(expected) < 0;
      case ">=" -> ((Comparable<Object>) real).compareTo(expected) >= 0;
      case "<=" -> ((Comparable<Object>) real).compareTo(expected) <= 0;
      default -> throw new IllegalArgumentException("Unknown operator: " + operator);
    };
  }

  public int getCurrentLevel() {
    return facts.stream().mapToInt(Fact::getLevel).max().orElse(0);
  }
}
