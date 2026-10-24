package com.ayyoub.inferenceengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FactsReader {

  /**
   * Read initial facts from a JSON array file. Value type is inferred from the JSON node type (boolean, int, double, string).
   *
   * @param filePath
   *          Path to facts.json
   * @return List of Fact objects with typed values
   * @throws IOException
   *           if the file cannot be read
   */
  public static List<Fact> readFacts(String filePath) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(new File(filePath));

    List<Fact> facts = new ArrayList<>();

    for (JsonNode node : root) {
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

      facts.add(new Fact(id, attribute, operator, value, confidence, derivedBy, level));
    }

    return facts;
  }
}
