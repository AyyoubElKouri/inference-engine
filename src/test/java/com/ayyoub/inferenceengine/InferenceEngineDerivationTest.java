package com.ayyoub.inferenceengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.engine.ConditionEvaluator.AbsenceWitness;
import com.ayyoub.inferenceengine.engine.Derivation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InferenceEngineDerivationTest {

  private static Fact fact(String id, String attribute, Object value, int level) {
    return new Fact(id, attribute, "=", value, 1.0, null, level);
  }

  private static Map<String, Object> leaf(String attribute, String operator, Object value) {
    return Map.of("attribute", attribute, "operator", operator, "value", value);
  }

  private static Rule rule(String id, Map<String, Object> condition, String conclusionAttribute, Object conclusionValue) {
    Rule rule = new Rule();
    rule.setRuleId(id);
    rule.setCondition(condition);
    rule.setConclusions(List.of(Map.<String, Object>of("attribute", conclusionAttribute, "operator", "=", "value", conclusionValue)));
    rule.setConfidence(1.0);

    return rule;
  }

  private static KnowledgeBase knowledgeBaseWith(Rule... rules) {
    KnowledgeBase knowledgeBase = new KnowledgeBase();
    knowledgeBase.getRules().clear();

    for (Rule rule : rules)
      knowledgeBase.addRule(rule);

    return knowledgeBase;
  }

  @Test
  void allRuleRecordsDerivationAndKeepsLevel() {
    WorkingMemory memory = new WorkingMemory();
    memory.addFact(fact("F1", "A", true, 0));
    memory.addFact(fact("F2", "B", true, 0));

    Rule r1 = rule("R1", Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true))), "G", true);
    new InferenceEngine(memory, knowledgeBaseWith(r1)).forwardChaining();

    Fact derived = memory.getFacts().stream().filter(fact -> fact.getAttribute().equals("G")).findFirst().orElseThrow();

    assertEquals(1, derived.getLevel());
    assertEquals("R1", derived.getDerivedBy());
    assertEquals(List.of(new Derivation("R1", derived.getFactId(), List.of("F1", "F2"), List.of())), memory.getDerivations());
  }

  @Test
  void negatedBranchRecordsAbsenceWitness() {
    WorkingMemory memory = new WorkingMemory();
    memory.addFact(fact("F1", "A", true, 0));
    memory.addFact(fact("F3", "C", false, 0));

    Rule r3 = rule("R3", Map.of("all", List.of(leaf("A", "=", true), Map.of("not", leaf("C", "=", true)))), "I", true);
    new InferenceEngine(memory, knowledgeBaseWith(r3)).forwardChaining();

    assertEquals(1, memory.getDerivations().size());

    Derivation derivation = memory.getDerivations().get(0);

    assertEquals("R3", derivation.ruleId());
    assertEquals(List.of("F1"), derivation.premiseIds());
    assertEquals(List.of(new AbsenceWitness("C", "=", true)), derivation.absences());
  }

  @Test
  void multiLevelRunChainsDerivations() {
    WorkingMemory memory = new WorkingMemory();
    memory.addFact(fact("F1", "A", true, 0));
    memory.addFact(fact("F2", "B", true, 0));
    memory.addFact(fact("F3", "C", false, 0));

    Rule r1 = rule("R1", Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true))), "G", true);
    Rule r3 = rule("R3", Map.of("all", List.of(leaf("A", "=", true), Map.of("not", leaf("C", "=", true)))), "I", true);
    Rule r10 = rule("R10", Map.of("all", List.of(leaf("G", "=", true), leaf("I", "=", true))), "P", true);
    new InferenceEngine(memory, knowledgeBaseWith(r1, r3, r10)).forwardChaining();

    Fact derived = memory.getFacts().stream().filter(fact -> fact.getAttribute().equals("P")).findFirst().orElseThrow();

    assertEquals(2, derived.getLevel());
    assertEquals(3, memory.getDerivations().size());
    assertTrue(memory.getDerivations().stream().anyMatch(derivation -> derivation.conclusionId().equals(derived.getFactId())));
  }
}
