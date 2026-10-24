package com.ayyoub.inferenceengine.domain;

/**
 * One fact in working memory or in the fact catalogue.
 */
public record Fact(
    String factId,
    String attribute,
    String operator,
    Object value,
    double confidence,
    String derivedBy,
    int level) {
}
