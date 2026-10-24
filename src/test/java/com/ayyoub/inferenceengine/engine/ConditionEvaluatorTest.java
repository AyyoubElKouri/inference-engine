package com.ayyoub.inferenceengine.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.domain.AbsenceWitness;
import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.engine.ConditionEvaluator.MatchResult;
import com.ayyoub.inferenceengine.engine.ConditionEvaluator.ProofResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {

  private final ConditionEvaluator evaluator = new ConditionEvaluator();

  private static Fact fact(String id, String attribute, Object value, int level) {
    return new Fact(id, attribute, "=", value, 1.0, null, level);
  }

  private static Map<String, Object> leaf(String attribute, String operator, Object value) {
    return Map.of("attribute", attribute, "operator", operator, "value", value);
  }

  @Test
  void leafMatchReturnsDeepestAttributeLevel() {
    MatchResult result = evaluator.evaluate(leaf("A", "=", true), List.of(fact("F1", "A", true, 2)));

    assertTrue(result.matched());
    assertEquals(2, result.depth());
  }

  @Test
  void leafMismatchKeepsAttributeDepth() {
    MatchResult result = evaluator.evaluate(leaf("A", "=", true), List.of(fact("F1", "A", false, 3)));

    assertFalse(result.matched());
    assertEquals(3, result.depth());
  }

  @Test
  void numbersCompareAcrossIntegerAndDouble() {
    List<Fact> facts = List.of(fact("F5", "score", 80, 0));

    assertTrue(evaluator.compare(80, "=", 80.0));
    assertTrue(evaluator.compare(80, ">", 70.5));
    assertFalse(evaluator.compare(80, "<", 70.5));
  }

  @Test
  void connectivesMatchAsDocumented() {
    List<Fact> facts = List.of(fact("F1", "A", true, 0), fact("F2", "B", true, 1), fact("F3", "C", false, 0));

    assertTrue(evaluator.evaluate(Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true))), facts).matched());
    assertFalse(evaluator.evaluate(Map.of("all", List.of(leaf("A", "=", true), leaf("C", "=", true))), facts).matched());
    assertTrue(evaluator.evaluate(Map.of("any", List.of(leaf("C", "=", true), leaf("B", "=", true))), facts).matched());
    assertTrue(evaluator.evaluate(Map.of("not", leaf("C", "=", true)), facts).matched());
    assertFalse(evaluator.evaluate(Map.of("xor", List.of(leaf("A", "=", true), leaf("B", "=", true))), facts).matched());
  }

  @Test
  void quantifiersCountMatchingBranches() {
    List<Map<String, Object>> branches = List.of(leaf("G", "=", true), leaf("H", "=", true), leaf("C", "=", true));
    List<Fact> facts = List.of(fact("F1", "G", true, 1), fact("F2", "H", true, 2));

    assertTrue(evaluator.evaluate(Map.of("at_least", 2, "of", branches), facts).matched());
    assertTrue(evaluator.evaluate(Map.of("exactly", 2, "of", branches), facts).matched());
    assertFalse(evaluator.evaluate(Map.of("at_most", 1, "of", branches), facts).matched());
  }

  @Test
  void emptyAndUnknownConditionsMatchNothing() {
    assertFalse(evaluator.evaluate(Map.of(), List.of(fact("F1", "A", true, 0))).matched());
    assertFalse(evaluator.evaluate(Map.of("unknown", List.of()), List.of()).matched());
    assertEquals(0, evaluator.evaluate(Map.of(), List.of()).depth());
  }

  @Test
  void allKeepsEveryPremise() {
    Map<String, Object> condition = Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true)));
    List<Fact> facts = List.of(fact("F1", "A", true, 0), fact("F2", "B", true, 1));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F1", "F2"), proof.premiseIds());
    assertEquals(1, proof.depth());
  }

  @Test
  void anyKeepsOnlyTheFirstSufficientPremise() {
    Map<String, Object> condition = Map.of("any", List.of(leaf("C", "=", true), leaf("D", "=", true)));
    List<Fact> facts = List.of(fact("F3", "C", true, 0), fact("F4", "D", true, 0));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertEquals(List.of("F3"), proof.premiseIds());
  }

  @Test
  void negatedBranchRecordsAnAbsenceWitness() {
    Map<String, Object> condition = Map.of("all", List.of(leaf("A", "=", true), Map.of("not", leaf("C", "=", true))));
    List<Fact> facts = List.of(fact("F1", "A", true, 0), fact("F3", "C", false, 0));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F1"), proof.premiseIds());
    assertEquals(List.of(new AbsenceWitness("C", "=", true)), proof.absences());
    assertEquals("C absent", proof.absences().get(0).label());
  }

  @Test
  void unmatchedProofIsEmpty() {
    ProofResult proof = evaluator.evaluateWithProvenance(Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true))),
        List.of(fact("F1", "A", true, 0)));

    assertFalse(proof.matched());
    assertTrue(proof.premiseIds().isEmpty());
    assertTrue(proof.absences().isEmpty());
  }

  @Test
  void nullNeverMatchesAndUnknownOperatorThrows() {
    assertFalse(evaluator.compare(null, "=", true));
    assertFalse(evaluator.compare(true, "=", null));
    assertThrows(IllegalArgumentException.class, () -> evaluator.compare(true, "≈", true));
  }

  @Test
  void sameClassComparablesOrder() {
    assertTrue(evaluator.compare("b", ">", "a"));
  }
}
