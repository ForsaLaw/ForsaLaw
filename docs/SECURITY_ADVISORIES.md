# Frontend dependency advisories

Snapshot: **2026-07-25** — `npm audit`, `frontend/`.

Refresh this file whenever `npm audit` output changes. An advisory that is knowingly
accepted is a decision; an advisory nobody has looked at is a liability.

## What was fixed

`npm audit fix` (no `--force`) resolved **3 of 8**, including the only *critical*. Only
`package-lock.json` changed — no `package.json` edits, no major upgrades, and
`npm run build` still passes.

| Package | Severity | Advisory |
|---|---|---|
| `websocket-driver` | **critical** | fixed in place |
| `postcss` | high | fixed in place |
| `@babel/core` | low | fixed in place |

## What remains — and why

**5 remaining: 3 high, 2 moderate.** Every one requires a **major** upgrade, which does not
belong in a compliance branch: a router or build-tool major is a functional change needing its
own branch, its own regression pass, and a manual run of the app.

### Build tooling — not shipped to users

| Package | Severity | Advisory |
|---|---|---|
| `vite` 5.4.21 | **high** | [GHSA-fx2h-pf6j-xcff](https://github.com/advisories/GHSA-fx2h-pf6j-xcff) — `server.fs.deny` bypass on Windows alternate paths |
| `vite` | moderate | [GHSA-4w7w-66w2-5vf9](https://github.com/advisories/GHSA-4w7w-66w2-5vf9) — path traversal in optimized deps `.map` handling |
| `vite` | moderate | [GHSA-v6wh-96g9-6wx3](https://github.com/advisories/GHSA-v6wh-96g9-6wx3) — `launch-editor` NTLMv2 hash disclosure via UNC paths |
| `esbuild` | moderate | [GHSA-67mh-4wv8-2f99](https://github.com/advisories/GHSA-67mh-4wv8-2f99) — dev server accepts cross-origin requests |

**Assessed impact: none in production.** All four are vulnerabilities in the **Vite dev
server**, which does not exist in the deployed system — production is a static bundle built at
image-build time and served by nginx (`frontend/Dockerfile`, `frontend/nginx.conf`). No Vite
code path runs at runtime.

The residual exposure is on **developer machines** running `npm run dev`. That is real but
bounded: a developer would have to visit a hostile page while the dev server is running.
Mitigation until the upgrade lands: don't run `npm run dev` on an untrusted network, and don't
browse untrusted sites in the same session.

Fix requires **vite 5 → 8** (three majors). Track separately.

### Runtime dependencies — need judgement

| Package | Severity | Advisory |
|---|---|---|
| `react-router` / `react-router-dom` 7.18.1 | **high** | [GHSA-qwww-vcr4-c8h2](https://github.com/advisories/GHSA-qwww-vcr4-c8h2) — RSC-mode CSRF bypass: action executes before the 400 response |
| `i18next-http-backend` 2.7.3 | moderate | [GHSA-q89c-q3h5-w34g](https://github.com/advisories/GHSA-q89c-q3h5-w34g) — path traversal / URL injection via unsanitised `lng`/`ns` |

**`react-router` — likely not exploitable here, but verify before dismissing.** The advisory
concerns **RSC (React Server Components) mode**. ForsaLaw is a client-side SPA built by Vite
with no server-side React rendering, so the affected code path should not be reachable.
Note the remediation npm proposes is a **downgrade to 7.11.0** (affected range is
`>=7.12.0 <8.3.0`) — a downgrade across a minor, so it needs routing regression testing.
CSRF is independently enforced server-side (Spring Security double-submit cookie), which is
the control that actually protects state-changing requests.

**`i18next-http-backend` — reachable only via the language code.** The `lng`/`ns` values feed
`loadPath: '/locales/{{lng}}/{{ns}}.json'`. `i18n.js` pins `supportedLngs: ['fr','en','ar']`
and a fixed namespace, so the values are not attacker-controlled through normal use.
Fix requires **2.x → 4.x**.

## Recommended follow-up

1. **`react-router`** — confirm RSC mode is genuinely unreachable, then decide: accept and
   document, or take the 7.11.0 downgrade with a routing regression pass. Highest-severity
   item touching runtime code.
2. **`i18next-http-backend` 2 → 4** — small surface, contained blast radius.
3. **`vite` 5 → 8** — largest change; schedule with a full frontend regression, ideally
   alongside other build-tooling work.
