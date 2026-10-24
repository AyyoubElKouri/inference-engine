package com.ayyoub.inferenceengine.domain;

import java.util.List;

/**
 * Provenance recorded when a rule fires, for one derived conclusion fact.
 */
public record Derivation(
    String ruleId,
    String conclusionId,
    List<String> premiseIds,
    List<AbsenceWitness> absences) {
}
