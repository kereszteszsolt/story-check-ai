# STC-002: Configured Ollama and OpenRouter models

## Status

Implemented

## User story

As a user, I want to choose a configured AI model so I can review a story with my preferred provider.

## Goal

Implement server-side provider/model configuration, an allowlisted model endpoint and Spring AI connections for Ollama and OpenRouter. Do not generate the final story review yet.

## Dependencies

STC-001 implemented and verified.

## Acceptance criteria

- [x] Read `OLLAMA_MODEL_1..3` and `OPENROUTER_MODEL_1..3`; trim, ignore empty slots, deduplicate model IDs per provider, and cap visible choices at three each.
- [x] `GET /api/models` returns only configured provider/model labels, without keys, internal endpoint credentials or unconfigured IDs.
- [x] OpenRouter models are selectable only with a server-side `OPENROUTER_API_KEY`; an absent key leaves them unavailable without blocking Ollama or app health.
- [x] Configure Spring AI Ollama and OpenAI-compatible OpenRouter models with bounded timeouts; support an explicit selected provider/model with no automatic paid fallback.
- [x] Validate requested model IDs against the enabled allowlist and return clear, safe errors for invalid choices and representative provider failures.
- [x] Add focused, deterministic tests for empty/duplicate slots, missing credentials, unknown model IDs and provider routing; CI must make no paid API calls.
- [x] Record actual checks and any untested real provider configurations in the release verification file.

## Out of scope

No live provider requirement without approval, automatic model discovery, new LLM integrations, pricing dashboard, RAG, tool calling, agents or persistent settings UI.
