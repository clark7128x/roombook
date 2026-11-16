# UI implementation review

## Source and authorization

The existing `docs/ui-prototype/` version 2 is the implementation reference. The user's instruction to implement all remaining tasks authorizes proceeding with its assets. The source directory and design documents remain unchanged.

The current prototype README records owner approval on 2026-11-01, with no comments, for T-32 and subsequent UI tasks. This supersedes the earlier report that approval was absent. Its 8-room example and incomplete slot links are prototype limitations; the implementation and final visual review use realistic data. The protected prototype directory remains unchanged by this implementation.

## Assets

- Five CSS files, six Fira Sans WOFF2 files, SIL OFL license, and the three-symbol SVG sprite copied from the reference.
- HTMX 2.0.4 from `https://raw.githubusercontent.com/bigskysoftware/htmx/v2.0.4/dist/htmx.min.js`; Zero-Clause BSD license copied from the same tag.
- HTMX SHA-256: `e209dda5c8235479f3166defc7750e1dbcd5a5c1808b7792fc2e6733768fb447`.
- Local `htmx-swap.js` enables semantic error swaps except 401 and disables evaluation and injected indicator styles. The shared shell must also set the initial HTMX configuration before library initialization.

The [T-30 browser report](browser-verification.md) records actual rendering with realistic fixtures, screenshots and qualified DR-01–DR-11 answers. Fira Sans and a keyboard focus sample were observed; complete glyph/number-width, 200% zoom, pixel comparison and target-browser checks remain unverified.

## Timeline evidence (T-33)

At T-33, the real Thymeleaf fragment measured 245,063 UTF-8 bytes for 20 rooms and 607,298 bytes for 50 rooms, without compression, with one one-hour booking in each room (`target/t33-test.log`). At T-29, R-17's fallback removed the optional link in each free slot while retaining the room-header booking action: the sizes are now 102,543 and 250,458 bytes. Both JVM zones produce the same measurements (`target/t30-full.log`, current `target/timeline-size-*.txt`); a regression assertion enforces the 300,000-byte limit. The model accepts kernel `Actor` rather than an accounts-internal DTO, preserving AR-03 while identifying owned bookings by user ID.

Configured days above 48 slots fail startup with property names. Legacy bookings may expand the displayed hours; overlaps are skipped with a WARN containing IDs only. Business properties now feed BookingRules rather than being ignored in favor of hard-coded defaults.
