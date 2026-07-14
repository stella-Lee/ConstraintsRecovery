# Repository Guidelines

## Project Structure & Module Organization

This repository is a Java-based research prototype for analyzing Product-ID-marked integrated source code in software product lines. Preserve the existing directory structure where reasonable:

- `src/main/java/spl/` contains production code for SPL analysis, scanning, signatures, and export logic.
- `src/test/java/spl/` contains tests that mirror the main package.
- `assets/` stores sample or input assets; `assets/elevator/` is the current domain fixture area.
- `output/` is for generated analysis results and local build artifacts. Do not commit generated files unless they are intentional fixtures.

The root Java package is `spl`. Keep new classes in this package or a deliberate subpackage under it.

## Build, Test, and Development Commands

Use Java 17 and Maven for all implementation work:

- `mvn test` compiles the project and runs the test suite. Run this after each implementation task.
- `mvn package` builds the project artifact after tests pass.
- `mvn exec:java -Dexec.mainClass=spl.SPLAnalyzerMain` may be used for local runs if the Maven exec plugin is configured.

## Coding Style & Naming Conventions

Use Java conventions: 4-space indentation, `PascalCase` class names, `camelCase` methods and fields, and `UPPER_SNAKE_CASE` constants. Keep one public top-level class per file, with the file name matching the class name. Prefer small, focused classes that reflect analyzer roles, such as scanners, extractors, signatures, and exporters. Do not delete non-empty files without explicit approval.

## Testing Guidelines

Place tests under `src/test/java/spl/` and name them after the class under test, for example `AssetFileScannerTest`. Use descriptive test method names that state the expected behavior. Add fixtures under `assets/` only when they are stable and minimal. Generated results used during test runs should go to `output/`.

## Milestone Scope

Implement one milestone at a time. Do not infer features, `requires`, or `excludes` relationships unless explicitly requested. The first milestone is limited to scanning asset files and extracting Product-ID signatures from `//#if`, `//#elif`, `//#else`, and `//#endif` directives.

## Commit & Pull Request Guidelines

The repository currently has no commit history, so no project-specific convention is established. Use concise, imperative commit subjects such as `Add asset scanner tests` or `Extract product ID signatures`. Pull requests should include a short summary, the milestone addressed, `mvn test` results, and any relevant sample input or output paths. Link related issues when available and call out changes to `assets/` or generated files explicitly.

## Security & Configuration Tips

Do not commit machine-specific IDE files, credentials, or large generated outputs. Keep local experiment data in `output/` and reusable fixtures in `assets/`.
