package com.ayyoub.inferenceengine.engine;

import com.ayyoub.inferenceengine.engine.ConditionEvaluator.AbsenceWitness;
import java.util.List;

/**
 * Provenance recorded when a rule fires, for one derived conclusion fact.
 *
 * <p>
 * {@code premiseIds} holds the minimal sufficient premise fact ids and {@code absences} holds one witness per negated branch that held on absence,
 * both captured at fire time so the trace view never recomputes them.
 */
public record Derivation(
    String ruleId,
    String conclusionId,
    List<String> premiseIds,
    List<AbsenceWitness> absences) {
}
