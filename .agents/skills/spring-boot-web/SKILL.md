---
name: spring-boot-web
description: Use for Java 21 Spring Boot MVC, static HTML/CSS/JavaScript UI, Jakarta validation, Maven build/test, Docker Compose and GitHub Actions in StoryCheck AI.
---

# Spring Boot and static web delivery

## Load only when

An approved story changes Spring MVC controllers/routes, request validation, static browser UI, Maven, container deployment or CI. Provider-specific prompting/model logic belongs to `spring-ai-providers`.

## Technical guidance

1. Inspect the relevant controller, request/response types, validation and focused tests; avoid scanning unrelated documentation.
2. Keep REST endpoints thin. Validate request payloads on the server. Put non-HTTP logic in a service, not in the controller.
3. Serve `src/main/resources/static/` from the **same** Spring Boot process as `/api`. Use plain HTML/CSS/JS and `fetch`; no separate frontend package/build, database or monorepo framework.
4. Show loading, empty-model, validation and error states; keep forms accessible. Insert untrusted model content as text, never raw HTML.
5. Keep Docker to one app service with host-installed Ollama and external OpenRouter. Never copy `.env` or API keys into an image. Document Linux host-gateway caveats when container access changes.
6. Use Java 21, Maven, idiomatic Spring Boot tests, MockMvc and focused browser checks. Pin compatible Spring Boot/Spring AI versions when the build is introduced.

## Verification

Run affected Maven tests and build before broader checks. Validate Compose config if deployment changes; manual browser smoke only after the UI exists. Add no billable model smoke tests to CI. Write exact outcomes in the active release verification.
