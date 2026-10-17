# REST contract

**Status: Health, error foundation and model listing implemented.** `/api/reviews` remains an STC-003 specification and currently returns 404.

## `GET /api/health`

Returns HTTP 200 with `{"status":"ok"}`. It does not require a configured LLM or call a provider. The same Spring Boot process serves `/` as a minimal static HTML entry point.

## Foundation errors and input boundary

Errors use `application/json` and the shape `{"code":"INVALID_REQUEST","message":"The request is invalid."}`. Raw input, exception details, paths and secrets are omitted. Framework headers such as `Allow` are retained.

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Malformed/missing body or failed input validation |
| 404 | `NOT_FOUND` | Missing resource or unimplemented endpoint |
| 405 | `METHOD_NOT_ALLOWED` | Unsupported HTTP method |
| 406 | `NOT_ACCEPTABLE` | Unsupported response format |
| 413 | `PAYLOAD_TOO_LARGE` | Body exceeds 65,536 bytes |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Unsupported request format |
| 500 | `INTERNAL_ERROR` | Unexpected failure |

All bodies read through Spring MVC `@RequestBody` are bounded to 65,536 bytes before deserialization, even without a reliable `Content-Length`. Jakarta validation is available; its invalid/missing/oversized DTO inputs are verified with a test-only controller, absent from the shipped app. The health endpoint has no input DTO. Domain constraints below remain planned for STC-003. Internal provider failures use the safe mappings in [Model configuration](model-configuration.md); their API rendering is checked with test-only routes, absent from the shipped app.

## `GET /api/models`

Returns only backend-configured, non-empty model slots. Empty slots are omitted. Model choices are grouped or labelled by provider and stable ID. An OpenRouter choice is exposed only if the server has credentials and a configured model. No secrets or unrestricted provider base URLs are returned.

Example shape:

```json
{
  "models": [
    {"provider":"ollama", "model":"qwen2.5:7b", "label":"Ollama · qwen2.5:7b"},
    {"provider":"ollama", "model":"llama3.2:3b", "label":"Ollama · llama3.2:3b"}
  ]
}
```

Example model values are illustrative; actual values come from backend environment configuration. With no enabled choices, the response is `{"models":[]}`. This endpoint never contacts providers, downloads models or tests model health. It accepts no arbitrary model or endpoint overrides.

## `POST /api/reviews`

Request example:

```json
{
  "provider": "ollama",
  "model": "qwen2.5:7b",
  "story": "As a user, I want to reset my password so that I can access my account."
}
```

Response example (mock/example text, **not a measured model output**):

```json
{
  "summary": "The reset mechanism and token expiration need clarification.",
  "acceptanceCriteria": ["A user can request a reset link.", "An expired token cannot reset a password."],
  "clarificationQuestions": ["How long should a reset token remain valid?"],
  "suggestedTests": ["An expired token is rejected without changing the password."]
}
```

Target constraints:

- The story is plain text with a documented maximum length (target: 4,000 characters) and a sensible minimum; reject blank or oversized input.
- The provider is `ollama` or `openrouter`, and the model must match an enabled server-side allowlist entry for that provider.
- `summary` is short; all arrays contain strings with bounded item counts and lengths. Allow **zero clarification questions** if nothing material is missing; do not invent weaknesses to fill a quota.
- Return the same shape for either provider; do not add raw LLM replies, provider headers, API keys, chain-of-thought, or local filesystem data.
- Handle invalid request (400), unavailable model/provider (422 or 503), rate limit (429), timeout (504), and invalid model JSON (502 or 422) with stable, non-sensitive error codes. Final mappings should be fixed and tested in STC-003.
- The frontend creates the **Markdown copy** locally from validated fields. This endpoint does not write any data.

## Data handling

The text is transmitted only to the **user-selected** model provider. No automatic switching from Ollama to OpenRouter. Do not log raw story text or the provider secret. Automated CI must use mocks and must never call paid APIs.
