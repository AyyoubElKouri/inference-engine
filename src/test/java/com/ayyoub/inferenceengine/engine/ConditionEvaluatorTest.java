package com.ayyoub.inferenceengine.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.Fact;
import com.ayyoub.inferenceengine.engine.ConditionEvaluator.AbsenceWitness;
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
  void leafMatchReturnsFactDepth() {
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
  void intFactEqualsDoubleValue() {
    MatchResult result = evaluator.evaluate(leaf("score", "=", 80.0), List.of(fact("F5", "score", 80, 0)));

    assertTrue(result.matched());
  }

  @Test
  void intFactOrdersAgainstDoubleValue() {
    List<Fact> facts = List.of(fact("F5", "score", 80, 0));

    assertTrue(evaluator.evaluate(leaf("score", ">", 70.5), facts).matched());
    assertFalse(evaluator.evaluate(leaf("score", "<", 70.5), facts).matched());
  }

  @Test
  void allRequiresEveryBranch() {
    Map<String, Object> condition = Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true)));

    assertTrue(evaluator.evaluate(condition, List.of(fact("F1", "A", true, 0), fact("F2", "B", true, 1))).matched());
    assertFalse(evaluator.evaluate(condition, List.of(fact("F1", "A", true, 0))).matched());
  }

  @Test
  void anyRequiresOneBranch() {
    Map<String, Object> condition = Map.of("any", List.of(leaf("C", "=", true), leaf("D", "=", true)));

    assertFalse(evaluator.evaluate(condition, List.of(fact("F3", "C", false, 0))).matched());
    assertTrue(evaluator.evaluate(condition, List.of(fact("F4", "D", true, 0))).matched());
  }

  @Test
  void notNegatesBranch() {
    Map<String, Object> condition = Map.of("not", leaf("C", "=", true));

    assertTrue(evaluator.evaluate(condition, List.of(fact("F3", "C", false, 0))).matched());
  }

  @Test
  void xorRequiresExactlyOneBranch() {
    Map<String, Object> condition = Map.of("xor", List.of(leaf("B", "=", true), leaf("C", "=", true)));

    List<Fact> oneTrue = List.of(fact("F2", "B", true, 0), fact("F3", "C", false, 0));
    List<Fact> bothTrue = List.of(fact("F2", "B", true, 0), fact("F3", "C", true, 0));

    assertTrue(evaluator.evaluate(condition, oneTrue).matched());
    assertFalse(evaluator.evaluate(condition, bothTrue).matched());
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
  void depthIsMaxOverBranches() {
    Map<String, Object> condition = Map.of("all", List.of(leaf("G", "=", true), leaf("H", "=", true)));
    List<Fact> facts = List.of(fact("F1", "G", true, 1), fact("F2", "H", true, 2));

    assertEquals(2, evaluator.evaluate(condition, facts).depth());
  }

  @Test
  void sameClassComparablesStillOrder() {
    assertTrue(evaluator.compare("b", ">", "a"));
  }

  @Test
  void nullNeverMatches() {
    assertFalse(evaluator.compare(null, "=", true));
    assertFalse(evaluator.compare(true, "=", null));
  }

  @Test
  void unknownOperatorThrows() {
    assertThrows(IllegalArgumentException.class, () -> evaluator.compare(true, "≈", true));
  }

  @Test
  void emptyConditionMatchesNothingAtDepthZero() {
    MatchResult result = evaluator.evaluate(Map.of(), List.of(fact("F1", "A", true, 0)));

    assertFalse(result.matched());
    assertEquals(0, result.depth());
  }

  @Test
  void r1AllKeepsBothPremises() {
    Map<String, Object> condition = Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true)));
    List<Fact> facts = List.of(fact("F1", "A", true, 0), fact("F2", "B", true, 1));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F1", "F2"), proof.premiseIds());
    assertTrue(proof.absences().isEmpty());
    assertEquals(1, proof.depth());
  }

  @Test
  void r2AnyKeepsOnlySufficientPremise() {
    Map<String, Object> condition = Map.of("any", List.of(leaf("C", "=", true), leaf("D", "=", true)));
    List<Fact> facts = List.of(fact("F3", "C", true, 0), fact("F4", "D", false, 0));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F3"), proof.premiseIds());
  }

  @Test
  void r2AnyPrefersFirstMatchedBranch() {
    Map<String, Object> condition = Map.of("any", List.of(leaf("C", "=", true), leaf("D", "=", true)));
    List<Fact> facts = List.of(fact("F3", "C", true, 0), fact("F4", "D", true, 0));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F3"), proof.premiseIds());
  }

  @Test
  void r3AllWithNotRecordsPremiseAndAbsence() {
    Map<String, Object> condition = Map.of("all", List.of(leaf("A", "=", true), Map.of("not", leaf("C", "=", true))));
    List<Fact> facts = List.of(fact("F1", "A", true, 0), fact("F3", "C", false, 0));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F1"), proof.premiseIds());
    assertEquals(List.of(new AbsenceWitness("C", "=", true)), proof.absences());
    assertEquals("C absent", proof.absences().get(0).label());
  }

  @Test
  void r4LeafGreaterRecordsPremise() {
    List<Fact> facts = List.of(fact("F5", "score", 80, 0));

    ProofResult proof = evaluator.evaluateWithProvenance(leaf("score", ">", 70), facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F5"), proof.premiseIds());
    assertTrue(proof.absences().isEmpty());
  }

  @Test
  void xorKeepsSingleMatchedBranch() {
    Map<String, Object> condition = Map.of("xor", List.of(leaf("B", "=", true), leaf("C", "=", true)));
    List<Fact> facts = List.of(fact("F2", "B", true, 0), fact("F3", "C", false, 0));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F2"), proof.premiseIds());
  }

  @Test
  void atLeastKeepsFirstNMatched() {
    List<Map<String, Object>> branches = List.of(leaf("G", "=", true), leaf("H", "=", true), leaf("J", "=", true));
    List<Fact> facts = List.of(fact("F1", "G", true, 0), fact("F2", "H", true, 0), fact("F3", "J", true, 0));

    ProofResult proof = evaluator.evaluateWithProvenance(Map.of("at_least", 2, "of", branches), facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F1", "F2"), proof.premiseIds());
  }

  @Test
  void exactlyKeepsEveryMatchedBranch() {
    List<Map<String, Object>> branches = List.of(leaf("G", "=", true), leaf("H", "=", true), leaf("C", "=", true));
    List<Fact> facts = List.of(fact("F1", "G", true, 1), fact("F2", "H", true, 2));

    ProofResult proof = evaluator.evaluateWithProvenance(Map.of("exactly", 2, "of", branches), facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F1", "F2"), proof.premiseIds());
  }

  @Test
  void atMostHoldsWithEmptyPremisesWhenNoneMatch() {
    List<Map<String, Object>> branches = List.of(leaf("C", "=", true), leaf("D", "=", true));
    List<Fact> facts = List.of(fact("F3", "C", false, 0), fact("F4", "D", false, 0));

    ProofResult proof = evaluator.evaluateWithProvenance(Map.of("at_most", 1, "of", branches), facts);

    assertTrue(proof.matched());
    assertTrue(proof.premiseIds().isEmpty());
    assertTrue(proof.absences().isEmpty());
  }

  @Test
  void unmatchedCarriesNoProof() {
    Map<String, Object> condition = Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true)));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, List.of(fact("F1", "A", true, 0)));

    assertFalse(proof.matched());
    assertTrue(proof.premiseIds().isEmpty());
    assertTrue(proof.absences().isEmpty());
  }

  @Test
  void evaluateKeepsLegacyVerdictAndDepth() {
    Map<String, Object> condition = Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true)));
    List<Fact> facts = List.of(fact("F1", "A", true, 0), fact("F2", "B", true, 1));

    MatchResult result = evaluator.evaluate(condition, facts);
    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertEquals(proof.matched(), result.matched());
    assertEquals(proof.depth(), result.depth());
  }
}
