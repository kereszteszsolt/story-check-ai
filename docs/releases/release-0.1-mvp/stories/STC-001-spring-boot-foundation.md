# STC-001: Spring Boot foundation and local delivery

## Status

Implemented

## User story

As a developer, I want one small Spring Boot app so I can build and run the AI demo without extra frontend infrastructure.

## Goal

Create the Java 21 Maven foundation, health API, static UI delivery boundary, and one-service Docker Compose setup. Keep Ollama outside Docker and apply the repository's short SPDX license headers to new handwritten source files.

## Dependencies

None. Use the existing documentation, diagrams and `.env.example` as the approved target design.

## Acceptance criteria

- [x] Pin compatible Java 21, Spring Boot and Spring AI versions in a working Maven build; document versions and start prerequisites.
- [x] One Spring Boot executable serves a minimal static HTML entry point and `GET /api/health` without a separate frontend build.
- [x] Validate HTTP inputs and return consistent, non-sensitive errors from the API foundation.
- [x] Add one-service Docker/Compose startup bound to localhost; document native development and host-Ollama connectivity without adding an Ollama container.
- [x] Add focused JUnit/MockMvc health and startup checks; record real Maven/build/Compose results in release verification.
- [x] Apply exactly the two-line, language-appropriate SPDX header to new handwritten source files; retain full Apache-2.0 `LICENSE` and README support assets.

## Out of scope

No model calls, provider selector, story analysis, database, monorepo tooling, separate frontend framework, authentication or UI polish. No automatic model downloads.
