---
name: spring-ai-providers
description: Use for Spring AI ChatClient, Ollama and OpenRouter model adapters, explicit provider/model selection, JSON Schema structured outputs, Java record validation, prompt tests and provider error handling.
---

# Spring AI provider integration

## Load only when

An approved story changes configured model choices, Ollama/OpenRouter connectivity, prompt construction, structured output/Java record mapping, or model-call tests. General web layout/build tasks belong to `spring-boot-web`.

## Technical guidance

1. Read only the active story, model configuration contract, relevant AI service and focused tests.
2. Expose **only** backend-allowlisted non-empty `OLLAMA_MODEL_1..3` and `OPENROUTER_MODEL_1..3`. Keep provider/model selection explicit; do not call a cloud model as fallback.
3. Use Spring AI `ChatClient` through two narrow provider configurations, sharing one story-review prompt/output contract. Use the OpenAI-compatible endpoint for OpenRouter; validate compatibility at implementation time.
4. Parse responses into a Java record and validate fields, item limits and sizes after parsing. Provider-native strict JSON Schema is model-dependent: test it rather than assuming it; if unsupported, handle the outcome explicitly.
5. Bound input size, output size, timeout, and retries; avoid hidden loops or accidental cloud costs. Do not include secrets in browser responses or logs.
6. Tests use fixed responses/mock clients; cover valid output, malformed JSON, unknown models, missing cloud credentials, timeouts and provider errors. Real-provider smoke tests require approval.

## Verification

Run the smallest relevant JUnit/Mockito/MockMvc tests, then a build. Report which models were **actually** tested; state `Not run` for live Ollama/OpenRouter tests when unavailable or not approved.
