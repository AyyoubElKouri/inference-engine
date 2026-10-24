package com.ayyoub.inferenceengine.service;

import com.ayyoub.inferenceengine.domain.Derivation;
import com.ayyoub.inferenceengine.domain.Fact;
import java.util.List;

/**
 * Immutable outcome of one inference run.
 */
public record InferenceResult(
    List<Fact> facts,
    List<Derivation> derivations) {
}
