# Inference Engine

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-UI-blue.svg)
![Maven](https://img.shields.io/badge/Build-Maven-success.svg)
![Guice](https://img.shields.io/badge/DI-Google_Guice-green.svg)

A rule-based inference engine (Expert System) developed in Java 21. This project uses the **forward chaining** mechanism to deduce new facts from a knowledge base (rules and initial facts) provided in JSON format.

## Key Features

* **Forward Chaining:** Iterative evaluation to infer new facts until the fact base is saturated.
* **Advanced Rule Conditions:** Support for complex logical operators:
  * Standard operators (`=`, `!=`, `<`, `>`, `<=`, `>=`)
  * Logical groups (`all` / AND, `any` / OR, `not`, `xor`)
  * Quantifiers (`at_least`, `exactly`, `at_most`)
* **Externalized Knowledge Base:** Rules and facts are dynamically loaded from JSON files (`facts.json`, `rules.json`).
* **Graphical User Interface (UI):** Desktop application built with JavaFX.
* **Dependency Injection:** Modular architecture powered by Google Guice.

## Tech Stack

* **Language:** Java 21
* **Graphical Interface:** JavaFX 21
* **Build Tool:** Maven (use `./mvnw`, never bare `mvn`)
* **Dependency Injection:** Google Guice 7.0.0
* **JSON Parsing:** Jackson Databind 2.17.1
* **Boilerplate:** Lombok 1.18.32
* **Formatting:** Spotless 2.43.0 with `eclipse-formatter.xml` (2-space indent, 150-col)

## Installation & Execution

### Prerequisites
* [Java Development Kit (JDK) 21](https://jdk.java.net/21/) (or later)
* No Maven install needed — the repo ships the `mvnw` wrapper.

### Run the project

Always run from the **repo root**: the readers load `src/main/resources/...` via relative paths, and running from another directory silently yields an empty rule base.

```bash
./mvnw javafx:run
```

The entry point is `com.ayyoub.inferenceengine.Launcher`, which starts `EngineApplication` and then `EngineUI` (`Launcher` → `EngineApplication` → `EngineUI`). Headless/CI environments must not launch JavaFX.

To build an executable fat JAR instead:

```bash
./mvnw package -DskipTests
java -jar target/inference-engine-0.0.1-SNAPSHOT.jar
```

## Configuration (JSON)

The rules and initial facts are defined in the `src/main/resources/` directory. You can redefine the expert's business logic by directly modifying these files.
- `facts.json`: top-level JSON array. Represents the Working Memory at startup.
- `rules.json`: rules nested under `knowledge_base.rules`. Each rule has `id`, `condition`, `conclusions`, `priority` (conflict-resolution order, highest first), `confidence` and `explanation`.

Condition DSL: leaf nodes use `attribute`/`operator`/`value`; groups use `all`/`any`/`not`/`xor`; quantifiers use `at_least`/`exactly`/`at_most` with `of`. Note: the legacy `InferenceEngine.forwardChaining` ignores `Rule.priority`.

## Development commands

```bash
# Fast compile check
./mvnw -q compile -DskipTests
```

```bash
# Launch the JavaFX UI (from repo root)
./mvnw javafx:run
```

```bash
# Format the code (run before committing)
./mvnw spotless:apply
```

```bash
# Build + spotless:check (fails on format drift)
./mvnw verify
```

Formatting is enforced by `spotless:check` during `verify` using `eclipse-formatter.xml` — not google-java-format.

## Tests

```bash
# Run tests (no test sources currently — succeeds with nothing to run)
./mvnw test
```
