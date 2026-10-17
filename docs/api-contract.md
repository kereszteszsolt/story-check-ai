# Planned REST contract

**Status: Proposed for Release 0.1.** These routes and examples are specifications, not running endpoints. STC-002/003 must implement and test them before documentation can claim runtime support.

## `GET /api/health`

Returns a small health response, such as `{"status":"ok"}`. It must not require access to a configured LLM or call a paid API.

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

Example model values are illustrative; actual values come from `.env`.

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
