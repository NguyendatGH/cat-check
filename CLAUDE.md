# CatCheck repository guide

Read `context/HANDOFF.md` for current status, then `context/spec/04-index.md` before implementation work. Do not read the generated, combined `context/spec/SPEC.md`; use `context/spec/04-map.md` to locate only the relevant sections.

Authority order:

1. `context/spec/00-decisions.md`
2. `context/spec/02-team-decisions.md`
3. `context/spec/03-arbitration.md`
4. `context/spec/parts/p*.md`

The stack is Spring Boot 4.1.1 on JDK 25, PostgreSQL 18.6, Flyway, React, TypeScript, Vite, OpenCV, and Docker Compose. Versions are pinned in `context/spec/reference/research-integrations.md`; do not choose replacement versions ad hoc.

Hard rules: look up schema names in p4, add only migrations listed in p4 section 4.9.2, do not log PII, do not make medical claims, and use the screen inventory instead of inventing UI content.
