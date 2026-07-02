# 1. Record architecture decisions

Date: 2026-06-12

## Status

Accepted

## Context

CLAUDE.md requires a short ADR for every significant architecture decision
(`docs/adr/`). We need a lightweight, consistent format.

## Decision

We use Architecture Decision Records, one Markdown file per decision, numbered
sequentially (`NNNN-title.md`), following Michael Nygard's format: Status,
Context, Decision, Consequences.

## Consequences

Decisions and their rationale stay versioned alongside the code, reducing the
bus-factor risk called out in TERV.md §10. The next ADR documents the T0
stack/skeleton choices.
