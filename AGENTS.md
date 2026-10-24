# AGENTS.md

## Commands (use `./mvnw`, never `mvn`)

- `./mvnw -q compile -DskipTests` — fast check.
- `./mvnw spotless:apply` — format before committing; `verify` runs `spotless:check` and fails on format drift.
- `./mvnw verify` — build + format check. Do not assume it runs tests cleanly (see below).
- UI: `./mvnw javafx:run` (main class `com.ayyoub.inferenceengine.Launcher`). Headless/CI work must not launch JavaFX.

## Do not trust README/HELP blindly

- `README.md` lists `./mvnw spring-boot:run` — wrong, there is no Spring dependency in `pom.xml`.
- `HELP.md` is the default Spring template — irrelevant to this repo.

## Source layout gotcha (critical)

- Maven compiles only `src/main/java`. The repo-root `engine/` directory (with `Engine.java`, `DefaultEngine.java`, …) is **not on the source path**: `target/classes/com/ayyoub/inferenceengine/` contains no `engine/` package after compile.
- Never add new source roots at repo top level. New engine code belongs under `src/main/java/com/ayyoub/inferenceengine/engine/`.
- Live model lives in `src/main/java/com/ayyoub/inferenceengine/`: `Rule`, `Fact` (Lombok `@Data`, mutable), legacy stateful `InferenceEngine` + `WorkingMemory` + `KnowledgeBase`, readers, `InferenceEngineUI`, Guice `config/AppModule` (currently empty bindings).

## Tests are broken — known state

- `src/test/.../InferenceEngineApplicationTests.java` imports `org.springframework.boot.test.context`, but `pom.xml` has only JUnit + Guice + JavaFX + Jackson + Lombok. `./mvnw test` fails compilation. Do not silently add Spring to fix it; flag to the user first.

## Runtime gotchas

- `RulesReader` / `FactsReader` / `KnowledgeBase` load via `new File("src/main/resources/...")` — always run from repo root, otherwise loading silently yields empty rules (constructor swallows to `new ArrayList<>()`).
- `facts.json` is a top-level JSON array; `rules.json` nests under `knowledge_base.rules`. Value types are inferred (boolean/int/double/string) — numeric comparisons must stay Number-safe (`Double.compare`), never raw `(Comparable)` casts across int/double.
- Condition DSL keys: leaf `attribute/operator/value`, groups `all/any/not/xor`, quantifiers `at_least/exactly/at_most` + `of`. `Rule.priority` is the conflict-resolution order (highest first); legacy `InferenceEngine.forwardChaining` ignores it, `DefaultEngine` sorts by it.

## Style — CRITICAL: apply before writing any code

- Formatter is `eclipse-formatter.xml` via Spotless (2-space, 150-col), not google-java-format.
- CRITICAL: Group code into logical blocks, one step or intention each. Separate blocks with exactly one blank line. No exceptions.
- CRITICAL: Javadoc is always multi-line, never `/** one-liner */`. The formatter cannot enforce this (stock JDT has no always-expand switch — the boundary flags only preserve the shape), so every new comment must be written expanded:
  ```java
  /**
   * ID of the fact.
   */
  ```
