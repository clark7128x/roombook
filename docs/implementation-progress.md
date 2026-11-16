# Implementation progress

The user requested continuous implementation of T-17 through T-33, with verification and commits for completed tasks. Design documents and `docs/ui-prototype/` remain read-only.

## Baseline

- T-01 through T-16: implemented. T-16 commit: `9c2f57f`.
- Baseline verification: 246 tests in UTC and 246 in America/Los_Angeles; 12 architecture checks in each zone.

## Execution order

T-17 → T-18 → T-19 → T-20 → T-21 → T-31 review → T-32 → T-22 → T-33 → T-23 → T-24 → T-25 → T-26 → T-27 → T-28 → T-29 → T-30.

This order follows the additional dependencies in the stage 9 addendum: T-22 requires T-32, and T-23 requires T-33. T-26/T-27 readiness components will be verified together where their dependencies overlap.

## Status

| Task | Status | Evidence / remaining work |
|---|---|---|
| T-17 | Implemented | 253 tests passed in each JVM zone; real HTTP login, session rotation/expiry/logout, cookie flags, live role/enablement, request cache and fail-closed reads verified. Log: `target/t17-full.log`. |
| T-18 | Implemented | Full suite: 257 tests per zone (`target/t18-full.log`); final filter-order change: 22 security/architecture tests per zone (`target/t18-final.log`). KZ-5 mutation failed on missing GET /api/v1/csrf, then matrix restored. |
| T-19 | Implemented | EP-01–07; strict local date-time query parsing; room lifecycle, Unicode/numeric boundaries, filters, office-date default and access matrix. Full suite: 262 tests per zone, `target/t19-full.log`. |
| T-20 | Implemented | EP-08–12, session author, cancellation/audit, paging, strict temporal input, 405 and conflict contracts. HTTP duplicate race and real 5-second lock timeout/recovery verified. 27 HTTP/security/architecture tests per zone, `target/t20-test.log`. |
| T-21 | Implemented | EP-13–19, redacted request records, password policy/reset, Ukrainian ordering and LAST_ADMIN. All 20 REST endpoints covered by access matrix. Full suite: 271 tests per zone, `target/t21-full.log`. |
| T-22 | Implemented | Shared shell/login, role menus, CSRF, UI/HTMX errors, request IDs, office-zone formatting and static caching. Full suite: 278 tests per zone, `target/t22-full.log`. |
| T-23 | Implemented; browser review with T-30 | Employee pages, filtered rooms, slot form, one-time success, escaped confirmation, conflict/OOB refresh and cancellation. Full suite: 334 tests per zone (`target/t23-t25-full.log`); final UI/security/architecture checks: 31 per zone (`target/t23-t24-final.log`). |
| T-24 | Implemented; browser review with T-30 | Admin booking filters, room/user forms and lifecycle rows, role/password fragments, safe errors and LAST_ADMIN. Full suite: 334 tests per zone; final UI/security/architecture: 31 per zone (`target/t23-t24-final.log`). |
| T-25 | Implemented | UC-01–06 and FR acceptance over real HTTP/PostgreSQL, 50-way slot race (1 success, 49 conflicts), strict deactivation-race outcomes, per-scenario invariant audit before fixture reset. Full suite: 334 tests per zone, `target/t23-t25-full.log`. |
| T-26 | Implemented | Structured lifecycle/authentication/start events; health and metrics on management port; readiness DOWN during test DB pause and UP after recovery while liveness remains UP. Sensitive-input markers absent from captured logs; one ERROR for an unexpected failure. 36 selected tests per zone, `target/t26-final.log`. |
| T-27 | Implemented; external deployment checks pending | Named fail-fast configuration checks, production DB role setup, pinned Temurin 21.0.13+9 image, private compose and CI workflow, runbook. 36 selected tests per zone (`target/t27-test.log`); local production container smoke passed in 32.90 s with UID 10001, read-only root, 1 GiB/2 CPU, bootstrap/login/secure cookie/Ukrainian sorting/readiness. See `docs/container-verification.md`. |
| T-28 | Implemented; production infrastructure acceptance pending | Encrypted custom-format backups, size/list checks, retention and systemd timer; minute probes, missing-minute availability, backup/disk/NTP alerts. Real isolated restore passed in 60.77 s with empty invariant audits; DB paused 180.5 s triggered alerts and recovered. See `docs/backup-monitoring-verification.md`; external storage/notification delivery and production-volume RTO remain unverified. |
| T-29 | Implemented; performance acceptance not passed | Full P1–P6 completed: zero 5xx, clean audit, P4 correctness 20/20. P3 HTML and P4 success p95 exceed 300 ms; P6 fresh after-GC evidence inconclusive. See `docs/load-verification.md`. Targeted P3 with reduced HTML and bcrypt 10 passed (GET p95 ≤88 ms); default cost 12 retained. |
| T-30 | Local verification recorded; formal acceptance incomplete | Million-row release/rollback passed in 149.38 s. Full suite: 339 tests per zone, including 13 architecture checks; final template changes: 11 tests per zone. Browser HX-01/02/05/10 and screenshots recorded in `docs/browser-verification.md`; target-browser checklist, 95 untagged IDs and external checks remain open in `docs/acceptance-risks.md`. |
| T-31 | Reference reviewed; approval recorded | Current prototype README records owner approval on 2026-11-01, without comments. Version 2 retained unchanged. See `docs/ui-implementation-review.md`. |
| T-32 | Implemented; browser checks with T-30 | Five CSS files, six fonts/license, icons and pinned local HTMX. Style/negative-fixture/contrast tests and architecture: 15 tests per zone, `target/t32-test.log`. |
| T-33 | Implemented | Immutable slot model, legacy-hour expansion, rounded spans, overlap WARN, ownership/past/now, accessible escaped fragment and startup 48-slot guard. 20 selected tests per zone, `target/t33-test.log`; 20/50-room fragments 245,063 / 607,298 UTF-8 bytes. |

## Acceptance boundaries

Record measured evidence, not assumed success. External production deployment facts, owner sign-off, unsupported browsers and hardware differences must be identified explicitly in the final verification report. Do not send external notifications or deploy to a real production system without explicit authorization for that destination.
