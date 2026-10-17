# Story-driven Codex workflow

## Status

Release 0.1 is **in progress** with the STC-001 application foundation and STC-002 provider configuration; STC-003–004 remain Planned. Follow the stories in their listed order after explicit approval. See the release verification for actual implementation and runtime evidence.

## Story format

Each story contains `Status`, `User story`, `Goal`, `Dependencies`, `Acceptance criteria`, and `Out of scope`. States are `Planned`, `In progress`, and `Implemented`. All Planned criteria remain unchecked; only actual verified results may justify checking them.

## Small development loop

1. Read `AGENTS.md`, the active release README and **one approved story**.
2. Name the intended files, scope and focused tests, then obtain **implementation approval** before editing application code.
3. Implement criteria in order, with one writer and the relevant technology skill only. Avoid parallel agents, duplicated architecture proposals and unnecessary test runs.
4. Run the narrowest applicable tests first. Ask separately before any external paid-model smoke test or consequential system/network change.
5. Log actual commands and results or `Not run` in the active release `verification.md`.
6. Review the diff, propose a small commit message and obtain **separate commit approval**. Never automatically push, force-push or start the next story.

## Evidence and source comments

Keep tests, environment details and blockers in verification, not in story acceptance checkboxes. Never claim a deployed app, screenshot, tested model or live provider call from planning documents alone. Keep code comments short and reason-focused. New handwritten source files use the two-line Apache-2.0 SPDX header described in `AGENTS.md`; general configuration/build files and Markdown/TOML/YAML/JSON docs do not need headers.

## Release closeout

STC-004 audits README instructions and local links, logs the final checked behavior, and replaces misleading planned statements **only for features actually verified**. The release remains open if any required acceptance criterion lacks proof.
