package com.ayyoub.inferenceengine.json;

import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.domain.Rule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson-backed parser for classpath resources.
 */
public final class JsonParserImpl implements JsonParser {

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public List<Rule> parseRules(String classpathResource) {
    JsonNode rulesNode = read(classpathResource).path("knowledge_base").path("rules");

    if (!rulesNode.isArray())
      throw new JsonParseException(classpathResource, new IllegalArgumentException("Expected knowledge_base.rules array"));

    try {
      return mapper.convertValue(rulesNode, new TypeReference<List<Rule>>() {
      });
    } catch (IllegalArgumentException error) {
      throw new JsonParseException(classpathResource, error);
    }
  }

  @Override
  public List<Fact> parseFacts(String classpathResource) {
    JsonNode root = read(classpathResource);

    if (!root.isArray())
      throw new JsonParseException(classpathResource, new IllegalArgumentException("Expected a top-level array"));

    List<Fact> facts = new ArrayList<>();

    for (JsonNode node : root)
      facts.add(fact(node));

    return List.copyOf(facts);
  }

  /**
   * Reads one resource and fails with its name on any problem.
   */
  private JsonNode read(String classpathResource) {
    try (InputStream input = JsonParserImpl.class.getResourceAsStream(classpathResource)) {
      if (input == null)
        throw new JsonParseException(classpathResource, new IOException("Resource not found"));

      return mapper.readTree(input);
    } catch (JsonParseException error) {
      throw error;
    } catch (IOException error) {
      throw new JsonParseException(classpathResource, error);
    }
  }

  /**
   * Maps one JSON fact node, inferring the value type.
   */
  private Fact fact(JsonNode node) {
    String id = node.path("id").asText("F?");
    String attribute = node.path("attribute").asText();
    String operator = node.path("operator").asText("=");
    double confidence = node.path("confidence").asDouble(1.0);
    int level = node.path("level").asInt(0);
    String derivedBy = node.path("derived_by").isNull() ? null : node.path("derived_by").asText();
    JsonNode valueNode = node.path("value");
    Object value;

    if (valueNode.isBoolean())
      value = valueNode.asBoolean();
    else if (valueNode.isInt())
      value = valueNode.asInt();
    else if (valueNode.isLong())
      value = (int) valueNode.asLong();
    else if (valueNode.isDouble() || valueNode.isFloat())
      value = valueNode.asDouble();
    else
      value = valueNode.asText();

    return new Fact(id, attribute, operator, value, confidence, derivedBy, level);
  }
}
