package com.ayyoub.inferenceengine.engine;

import com.ayyoub.inferenceengine.domain.Derivation;
import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.domain.Rule;
import com.ayyoub.inferenceengine.engine.ConditionEvaluator.ProofResult;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;

/**
 * Forward-chaining engine over the priority-ordered knowledge base.
 */
public final class InferenceEngine {

  private final KnowledgeBase knowledgeBase;
  private final ConditionEvaluator evaluator;

  /**
   * Creates the engine.
   */
  @Inject
  public InferenceEngine(KnowledgeBase knowledgeBase, ConditionEvaluator evaluator) {
    this.knowledgeBase = knowledgeBase;
    this.evaluator = evaluator;
  }

  /**
   * Derives facts until a full pass over the rules adds nothing new.
   */
  public void run(WorkingMemory memory) {
    List<Rule> rules = knowledgeBase.rules();

    // Start past the highest existing numeric id so derived ids never
    // collide with seed facts.
    int counter = memory.facts().stream().map(Fact::factId).filter(id -> id != null && id.matches("F\\d+"))
        .mapToInt(id -> Integer.parseInt(id.substring(1))).max().orElse(memory.facts().size()) + 1;
    boolean factsChanged = true;

    // Keep applying rules until a complete pass adds no new facts.
    while (factsChanged) {
      factsChanged = false;

      for (Rule rule : rules) {
        ProofResult proof = evaluator.evaluateWithProvenance(rule.condition(), memory.facts());

        if (!proof.matched())
          continue;

        for (Map<String, Object> conclusion : rule.conclusions()) {
          String attribute = (String) conclusion.get("attribute");
          String operator = (String) conclusion.get("operator");
          Object value = conclusion.get("value");

          if (conclusionExists(memory, attribute, operator, value))
            continue;

          int level = proof.depth() + 1;
          String conclusionId = "F" + counter++;

          memory.addFact(new Fact(conclusionId, attribute, operator, value, rule.confidence(), rule.ruleId(), level));
          memory.addDerivation(new Derivation(rule.ruleId(), conclusionId, proof.premiseIds(), proof.absences()));
          factsChanged = true;
        }
      }
    }
  }

  /**
   * True when a fact with the same attribute already satisfies the conclusion value.
   */
  private boolean conclusionExists(WorkingMemory memory, String attribute, String operator, Object value) {
    return memory.facts().stream().anyMatch(fact -> fact.attribute().equals(attribute) && evaluator.compare(fact.value(), operator, value));
  }
}
