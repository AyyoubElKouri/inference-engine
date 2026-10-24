package com.ayyoub.inferenceengine;

import com.ayyoub.inferenceengine.engine.ConditionEvaluator;
import com.ayyoub.inferenceengine.engine.Derivation;
import java.util.ArrayList;
import java.util.List;

public class WorkingMemory {
  /**
   * List of facts currently in the working memory.
   */
  private List<Fact> facts;

  /**
   * Provenance recorded per firing, in firing order; read by the trace view.
   */
  private final List<Derivation> derivations = new ArrayList<>();

  /**
   * Single shared comparison logic; storage itself stays dumb.
   */
  private final ConditionEvaluator evaluator = new ConditionEvaluator();

  /**
   * Constructor
   */
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
   * Record the provenance of one firing, for one derived conclusion fact.
   *
   * @param derivation
   *          The derivation to record.
   */
  public void addDerivation(Derivation derivation) {
    derivations.add(derivation);
  }

  /**
   * Get the derivations recorded during forward chaining, in firing order.
   *
   * @return The list of derivations.
   */
  public List<Derivation> getDerivations() {
    return derivations;
  }

  /**
   * Check if a fact with the given attribute, operator, and value exists in the working memory.
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
    // Matching delegates to the evaluator so comparison lives in exactly one
    // module.
    return facts.stream().anyMatch(fact -> fact.getAttribute().equals(attribute) && evaluator.compare(fact.getValue(), operator, value));
  }

  public int getCurrentLevel() {
    return facts.stream().mapToInt(Fact::getLevel).max().orElse(0);
  }
}
