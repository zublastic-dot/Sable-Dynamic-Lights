# Sable Dynamic Lights fork

Read zublastic-dot/zublastic-context BOOTSTRAP.md and follow its authority and
provenance rules. Source is authoritative for implementation; exact installed
artifacts and runtime evidence are authoritative for live behavior.

Before writes, read ModAutomator AGENTS.md, docs/AI_HANDOFF.md, docs/LIVE_STATE.md,
docs/REPOSITORY_SAFETY.md and docs/AGENT_CONCURRENCY.md. Acquire a live canonical
CAS lease covering domain:modautomator:compatibility/sable-dynamic-lighting and
the physical paths, use the lease's work branch/base and add its work manifest.
Record material learning in zublastic-context and mirror changes in zublastic-ideas.

Retain upstream attribution and license. Client lighting must never change server
light storage or reads. Scope caches by actual client-level identity; clear and
invalidate the old level on transition/unload. Preserve Create/Sable client lighting.
Run tests/build, inspect hosted checks, and distinguish runtime acceptance from
unit tests. Never replace a running game's JAR; retain a verified rollback.
