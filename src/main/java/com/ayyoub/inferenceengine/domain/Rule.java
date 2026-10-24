package com.ayyoub.inferenceengine.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * One inference rule loaded from rules.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Rule(
    @JsonProperty("id") String ruleId,
    @JsonProperty("name") String name,
    @JsonProperty("condition") Map<String, Object> condition,
    @JsonProperty("conclusions") List<Map<String, Object>> conclusions,
    @JsonProperty("priority") int priority,
    @JsonProperty("confidence") double confidence,
    @JsonProperty("explanation") String explanation) {
}
