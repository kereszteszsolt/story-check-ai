# STC-003: Structured story review and validation

## Status

Planned

## User story

As a user, I want clear review suggestions so I can improve my user story before development starts.

## Goal

Turn a short user story into a consistent Java record response with concise feedback, acceptance criteria, clarification questions and suggested tests, using either configured provider.

## Dependencies

STC-002 implemented and verified.

## Acceptance criteria

- [ ] `POST /api/reviews` accepts an explicit configured provider/model and a bounded plain-text story; blank, oversized and unknown-model inputs are rejected.
- [ ] Share one concise Spring AI prompt/template and one typed Java record response contract across Ollama and OpenRouter, without duplicating review rules in adapters.
- [ ] Return `summary`, `acceptanceCriteria`, `clarificationQuestions`, and `suggestedTests` as JSON arrays/strings according to `docs/api-contract.md`.
- [ ] Parse with Spring AI structured output and validate the resulting record, field lengths and list limits on the server; handle malformed/unsupported model output safely.
- [ ] Bound timeouts, output size and any retries; never leak keys, raw provider errors or story text in logs, and never silently switch to a paid provider.
- [ ] Add deterministic unit/MockMvc tests for a valid record, invalid output, no-required-questions case, provider errors and safe HTTP responses.
- [ ] Record exact mocked checks and mark real Ollama/OpenRouter smoke checks `Not run` unless separately approved.

## Out of scope

No persistence, automatic story editing, hallucination-free guarantees, RAG, agent tools, asynchronous jobs or UI redesign.
