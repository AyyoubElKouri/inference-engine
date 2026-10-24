package com.ayyoub.inferenceengine.json;

/**
 * Unchecked failure while loading or parsing a JSON resource.
 */
public class JsonParseException extends RuntimeException {

  private final String resource;

  /**
   * Creates the failure for one resource.
   */
  public JsonParseException(String resource, Throwable cause) {
    super("Could not parse " + resource + ": " + cause.getMessage(), cause);
    this.resource = resource;
  }

  /**
   * Resource name that failed.
   */
  public String resource() {
    return resource;
  }
}
