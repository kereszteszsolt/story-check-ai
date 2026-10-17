# STC-004: Minimal UI, Markdown copy and release verification

## Status

Implemented

## User story

As a user, I want one simple page so I can review a story and copy its suggested improvements.

## Goal

Deliver the small responsive HTML/CSS/Vanilla JS frontend and finish the real validation, testing, documentation and release evidence for the MVP.

## Dependencies

STC-003 implemented and verified.

## Acceptance criteria

- [x] The Spring Boot-served page loads `GET /api/models` and exposes only available provider/model choices, with clear empty, loading and error states.
- [x] A user can enter a story, submit it through `POST /api/reviews`, and read the validated review fields in distinct accessible sections.
- [x] **Copy Markdown** produces a readable heading-and-list representation of the displayed review, with feedback for copy failure and no server-side save.
- [x] The UI handles mobile widths, keyboard navigation, invalid input, timeout and provider errors without unsafe HTML injection.
- [x] Focused JUnit/Mockito/MockMvc tests, a Maven build and Docker Compose startup pass or have recorded blockers; GitHub Actions runs mocked tests/build without API keys or paid model calls.
- [x] Verify one actual browser flow using synthetic story text; test real Ollama and/or OpenRouter only when configured and separately approved, recording models and costs/limits where known.
- [x] Update the README's planned/runnable claims to match **only verified** behavior, check Mermaid diagrams/links, and record commands/results in release verification before marking any acceptance criterion complete.

## Out of scope

No authentication, database, file upload, chat history, streaming response requirement, comparison mode, MCP, agent workflows or next release planning.
