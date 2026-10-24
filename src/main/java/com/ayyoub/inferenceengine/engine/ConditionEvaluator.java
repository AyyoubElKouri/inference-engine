package com.ayyoub.inferenceengine.engine;

import com.ayyoub.inferenceengine.Fact;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConditionEvaluator {

  /**
   * Verdict plus deepest depended-on fact level, from a single tree walk.
   */
  public record MatchResult(
      boolean matched,
      int depth) {
  }

  /**
   * Witness that a negated branch holds because its leaf is absent from working memory.
   *
   * <p>
   * The witness names the unsatisfied leaf so the trace view can render a dashed absence edge from a badge instead of a solid premise edge.
   */
  public record AbsenceWitness(
      String attribute,
      String operator,
      Object value) {

    /**
     * Short badge text for the trace graph, such as {@code "C absent"}.
     */
    public String label() {
      return attribute + " absent";
    }
  }

  /**
   * Match verdict with minimal-proof provenance, from a single tree walk.
   *
   * <p>
   * When {@code matched} is true, {@code premiseIds} holds the fact ids of the minimal sufficient set that fired the rule and {@code absences} holds
   * one witness per negated branch that held on absence. When {@code matched} is false, both lists are empty.
   */
  public record ProofResult(
      boolean matched,
      int depth,
      List<String> premiseIds,
      List<AbsenceWitness> absences) {
  }

  /**
   * Evaluates a condition tree against facts, returning match verdict and dependency depth.
   */
  public MatchResult evaluate(Map<String, Object> condition, List<Fact> facts) {
    // Provenance evaluation computes the same verdict and depth; this
    // overload only drops the proof details for legacy callers.
    ProofResult proof = evaluateWithProvenance(condition, facts);

    return new MatchResult(proof.matched(), proof.depth());
  }

  /**
   * Evaluates a condition tree and returns the minimal sufficient proof.
   *
   * <p>
   * Minimality per connective: {@code all} keeps every matched child because all contributed; {@code any} keeps the first matched child in documented
   * order; {@code xor} keeps its single matched child; {@code at_least} keeps the first N matched children; {@code exactly} and {@code at_most} keep
   * every matched child; {@code not} holding on absence contributes an absence witness instead of premise ids. Depth keeps the legacy definition
   * (deepest level over every consulted branch) so derived fact levels are unchanged.
   */
  public ProofResult evaluateWithProvenance(Map<String, Object> condition, List<Fact> facts) {
    // A condition is either a leaf (attribute/operator/value) or a boolean
    // connective over child conditions.
    if (condition.containsKey("attribute"))
      return proveLeaf(condition, facts);

    if (condition.containsKey("not"))
      return proveNot(condition, facts);

    List<Map<String, Object>> children = childConditions(condition);
    List<ProofResult> proofs = children.stream().map(child -> evaluateWithProvenance(child, facts)).toList();
    int depth = proofs.stream().mapToInt(ProofResult::depth).max().orElse(0);

    if (condition.containsKey("all"))
      return combine(depth, proofs.stream().allMatch(ProofResult::matched), proofs);

    if (condition.containsKey("any")) {
      List<ProofResult> sufficient = firstMatched(proofs, 1);

      return combine(depth, !sufficient.isEmpty(), sufficient);
    }

    if (condition.containsKey("xor"))
      return combine(depth, matchedCount(proofs) == 1, matchedOnly(proofs));

    if (condition.containsKey("at_least"))
      return combine(depth, matchedCount(proofs) >= required(condition, "at_least"), firstMatched(proofs, required(condition, "at_least")));

    if (condition.containsKey("exactly"))
      return combine(depth, matchedCount(proofs) == required(condition, "exactly"), matchedOnly(proofs));

    if (condition.containsKey("at_most"))
      return combine(depth, matchedCount(proofs) <= required(condition, "at_most"), matchedOnly(proofs));

    return new ProofResult(false, 0, List.of(), List.of());
  }

  /**
   * Compares a fact value against an expected value; numbers compare by value across int/double.
   */
  public boolean compare(Object real, String operator, Object expected) {
    if (real == null || expected == null)
      return false;

    return switch (operator) {
      case "=" -> valuesEqual(real, expected);
      case "!=" -> !valuesEqual(real, expected);
      case ">" -> compareOrdered(real, expected) > 0;
      case "<" -> compareOrdered(real, expected) < 0;
      case ">=" -> compareOrdered(real, expected) >= 0;
      case "<=" -> compareOrdered(real, expected) <= 0;
      default -> throw new IllegalArgumentException("Unknown operator: " + operator);
    };
  }

  /**
   * Matches one leaf against facts; the proof keeps the deepest matching fact as the sufficient premise.
   */
  private ProofResult proveLeaf(Map<String, Object> condition, List<Fact> facts) {
    String attribute = (String) condition.get("attribute");
    String operator = (String) condition.get("operator");
    Object value = condition.get("value");

    // One sufficient fact proves a leaf; the deepest match keeps depth
    // consistent with the legacy definition below.
    Fact best = null;

    for (Fact fact : facts) {
      if (fact.getAttribute().equals(attribute) && compare(fact.getValue(), operator, value) && (best == null || fact.getLevel() > best.getLevel()))
        best = fact;
    }

    int depth = facts.stream().filter(f -> f.getAttribute().equals(attribute)).mapToInt(Fact::getLevel).max().orElse(0);

    if (best == null)
      return new ProofResult(false, depth, List.of(), List.of());

    return new ProofResult(true, depth, List.of(best.getFactId()), List.of());
  }

  /**
   * Negates one branch; a negation that holds contributes absence witnesses instead of premises.
   */
  @SuppressWarnings("unchecked")
  private ProofResult proveNot(Map<String, Object> condition, List<Fact> facts) {
    Map<String, Object> child = (Map<String, Object>) condition.get("not");
    ProofResult inner = evaluateWithProvenance(child, facts);

    // A negation that fails contributes no proof; one that holds on
    // absence contributes witnesses instead of premise edges.
    if (inner.matched())
      return new ProofResult(false, inner.depth(), List.of(), List.of());

    List<AbsenceWitness> witnesses = new ArrayList<>(inner.absences());

    if (witnesses.isEmpty())
      witnesses.add(witnessFor(child));

    return new ProofResult(true, inner.depth(), List.of(), List.copyOf(witnesses));
  }

  /**
   * Builds a matched proof from the sufficient subset, or an empty unmatched proof.
   */
  private ProofResult combine(int depth, boolean matched, List<ProofResult> sufficient) {
    if (!matched || sufficient.stream().anyMatch(proof -> !proof.matched()))
      return new ProofResult(false, depth, List.of(), List.of());

    return new ProofResult(true, depth, unionPremises(sufficient), unionAbsences(sufficient));
  }

  /**
   * Names the unsatisfied leaf of a failed branch for the absence badge.
   */
  private AbsenceWitness witnessFor(Map<String, Object> condition) {
    if (condition.containsKey("attribute"))
      return new AbsenceWitness((String) condition.get("attribute"), (String) condition.get("operator"), condition.get("value"));

    String kind = List.of("all", "any", "xor", "not", "at_least", "exactly", "at_most").stream().filter(condition::containsKey).findFirst()
        .orElse("condition");

    return new AbsenceWitness(kind, "=", null);
  }

  /**
   * First N matched branches in documented order; the smallest sufficient subset for any/at_least.
   */
  private List<ProofResult> firstMatched(List<ProofResult> proofs, int count) {
    return proofs.stream().filter(ProofResult::matched).limit(count).toList();
  }

  /**
   * Every matched branch; the sufficient set for xor/exactly/at_most.
   */
  private List<ProofResult> matchedOnly(List<ProofResult> proofs) {
    return proofs.stream().filter(ProofResult::matched).toList();
  }

  /**
   * Premise ids across the sufficient subset, deduplicated in encounter order.
   */
  private List<String> unionPremises(List<ProofResult> proofs) {
    return proofs.stream().flatMap(proof -> proof.premiseIds().stream()).distinct().toList();
  }

  /**
   * Absence witnesses across the sufficient subset, deduplicated in encounter order.
   */
  private List<AbsenceWitness> unionAbsences(List<ProofResult> proofs) {
    return proofs.stream().flatMap(proof -> proof.absences().stream()).distinct().toList();
  }

  /**
   * Returns the child list under all/any/xor/of, or empty when the node holds none.
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> childConditions(Map<String, Object> condition) {
    for (String key : List.of("all", "any", "xor", "of"))
      if (condition.containsKey(key))
        return (List<Map<String, Object>>) condition.get(key);

    return List.of();
  }

  /**
   * Branches that matched, for xor and quantifier connectives.
   */
  private long matchedCount(List<ProofResult> proofs) {
    return proofs.stream().filter(ProofResult::matched).count();
  }

  /**
   * Required branch count declared by a quantifier node.
   */
  private int required(Map<String, Object> condition, String key) {
    return (int) condition.get(key);
  }

  /**
   * Equality with numeric values compared by value instead of boxed equals.
   */
  private boolean valuesEqual(Object real, Object expected) {
    if (real instanceof Number realNumber && expected instanceof Number expectedNumber)
      return Double.compare(realNumber.doubleValue(), expectedNumber.doubleValue()) == 0;

    return real.equals(expected);
  }

  /**
   * Ordering with numerics via Double.compare; same-class Comparables use compareTo.
   */
  @SuppressWarnings("unchecked")
  private int compareOrdered(Object real, Object expected) {
    if (real instanceof Number realNumber && expected instanceof Number expectedNumber)
      return Double.compare(realNumber.doubleValue(), expectedNumber.doubleValue());

    if (real instanceof Comparable<?> comparable && real.getClass().isInstance(expected))
      return ((Comparable<Object>) comparable).compareTo(expected);

    throw new IllegalArgumentException("Cannot order " + real.getClass().getSimpleName() + " against " + expected.getClass().getSimpleName());
  }
}
