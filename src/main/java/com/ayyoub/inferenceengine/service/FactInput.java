package com.ayyoub.inferenceengine.service;

/**
 * One fact the user chose to assert before a run.
 */
public record FactInput(
    String attribute,
    Object value) {
}
