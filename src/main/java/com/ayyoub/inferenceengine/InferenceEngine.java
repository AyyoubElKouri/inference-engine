package com.ayyoub.inferenceengine;

import com.ayyoub.inferenceengine.engine.ConditionEvaluator;
import com.ayyoub.inferenceengine.engine.ConditionEvaluator.ProofResult;
import com.ayyoub.inferenceengine.engine.Derivation;
import java.util.List;
import java.util.Map;

public class InferenceEngine {

  private final WorkingMemory workingMemory;
  private final KnowledgeBase knowledgeBase;
  private final ConditionEvaluator evaluator;

  public InferenceEngine(WorkingMemory workingMemory, KnowledgeBase knowledgeBase) {
    this(workingMemory, knowledgeBase, new ConditionEvaluator());
  }

  /**
   * Creates the engine with a caller-supplied evaluator, so tests can substitute it.
   */
  public InferenceEngine(WorkingMemory workingMemory, KnowledgeBase knowledgeBase, ConditionEvaluator evaluator) {
    this.workingMemory = workingMemory;
    this.knowledgeBase = knowledgeBase;
    this.evaluator = evaluator;
  }

  /**
   * Derives facts until a full pass over the rules adds nothing new.
   */
  public void forwardChaining() {
    List<Rule> rules = knowledgeBase.getRules();

    // Start past the highest existing numeric id so derived ids never
    // collide with seed facts (which the trace graph keys edges on).
    int counter = workingMemory.getFacts().stream().map(Fact::getFactId).filter(id -> id != null && id.matches("F\\d+"))
        .mapToInt(id -> Integer.parseInt(id.substring(1))).max().orElse(workingMemory.getFacts().size()) + 1;
    boolean factsChanged = true;

    // Keep applying rules until a complete pass adds no new facts. This
    // reaches the fixed point needed for conclusions derived in stages.
    while (factsChanged) {
      factsChanged = false;
      for (Rule rule : rules) {
        ProofResult proof = evaluator.evaluateWithProvenance(rule.getCondition(), asDomainFacts(workingMemory.getFacts()));

        if (proof.matched()) {
          for (Map<String, Object> conclusion : rule.getConclusions()) {
            String attribute = (String) conclusion.get("attribute");
            String operator = (String) conclusion.get("operator");
            Object value = conclusion.get("value");

            if (!workingMemory.factExists(attribute, operator, value)) {
              // A fact's level is one greater than the deepest
              // fact used by the rule that produced it.
              int newLevel = proof.depth() + 1;
              String conclusionId = "F" + counter++;

              workingMemory.addFact(new Fact(conclusionId, attribute, operator, value, rule.getConfidence(), rule.getRuleId(), newLevel));

              // Provenance is captured at fire time so the trace view
              // draws the minimal proof without recomputing matches.
              workingMemory.addDerivation(new Derivation(rule.getRuleId(), conclusionId, proof.premiseIds(), proof.absences()));
              factsChanged = true;
            }
          }
        }
      }
    }
  }

  /**
   * Adapts legacy mutable facts to the immutable domain records consumed by the evaluator.
   */
  private static List<com.ayyoub.inferenceengine.domain.Fact> asDomainFacts(List<Fact> facts) {
    return facts.stream().map(fact -> new com.ayyoub.inferenceengine.domain.Fact(fact.getFactId(), fact.getAttribute(), fact.getOperator(),
        fact.getValue(), fact.getConfidence(), fact.getDerivedBy(), fact.getLevel())).toList();
  }
}
