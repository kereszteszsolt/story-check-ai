# Release 0.1 — Story Review MVP

## Status

**STC-001–004 implemented and verified locally.** The app has a responsive review UI, Markdown copy/manual fallback, health/model/review APIs, explicit Spring AI adapters, a shared JSON Schema/validated review record, deterministic tests and one-service Docker packaging. The GitHub Actions workflow is configured. Live model compatibility, a hosted workflow run and public deployment remain unverified. See verification for measured results and limitations; no published release is claimed.

## Goal

Deliver one small Spring Boot web app: choose a configured Ollama/OpenRouter model, paste a user story, receive validated acceptance criteria, clarification questions and test suggestions, then copy the result as Markdown.

## Boundaries

- One Java 21 Spring Boot application (static HTML/JS UI + REST API), Maven and one app Docker service.
- Up to three manually configured Ollama and three OpenRouter model slots; blank slots hidden. A key is required for OpenRouter.
- Spring AI `ChatClient`, Java record/JSON Schema structured output, server-side validation.
- Unit/API tests, deterministic provider mocks, minimal GitHub Actions and honest manual smoke evidence.
- No database, accounts, RAG, LangChain, LangGraph, agent runtime, tool calling or MCP.

## Story order

| Story | Scope | Status |
| --- | --- | --- |
| [STC-001](stories/STC-001-spring-boot-foundation.md) | Spring Boot foundation, build, Compose and health | Implemented |
| [STC-002](stories/STC-002-configured-llm-providers.md) | Explicit Ollama/OpenRouter model registry and connection | Implemented |
| [STC-003](stories/STC-003-structured-story-review.md) | Structured story review prompt, Java records and safe errors | Implemented |
| [STC-004](stories/STC-004-ui-tests-and-release-verification.md) | One-page UI, Markdown copy, tests, CI and truthful docs | Implemented |

[Verification](verification.md) records actual approved checks; later stories stay unverified until implemented.
