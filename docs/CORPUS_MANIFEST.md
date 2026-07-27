# Tunisian Legal Corpus — Availability Manifest

**Audit date:** 2026-07-26. All findings below were verified by direct HTTP probe and by
running text extraction with **PDFBox 3.0.1 + `setSortByPosition(true)`** — i.e. the exact
configuration in `PdfTextExtractor`. Numbers are reproducible.

> Audit was run from a non-Tunisian IP. Two sources behaved in ways that may be
> IP-dependent (`legislation.tn`, `droit-afrique.com`) — flagged individually below and
> worth re-checking from Tunisia before writing them off.

---

## Headline result

The premise that Tunisian jurisprudence is undigitised is **wrong for the Court of
Cassation**. `cassation.tn` publishes ruling PDFs spanning 1991–2024, free,
unauthenticated, and — critically — **with real Arabic text layers**. The full harvest
collected **5,340 rulings, of which 5,339 (99.98%) carry a usable text layer**; exactly one
is a scan.

**No OCR is required to begin Tier 2 ingestion.** `docs/AI_RAG_TUNISIA_STRATEGY.md`
assumes an "OCR pipeline on scanned bulletins" for Tier 2; that assumption should be
revised — OCR is needed only for the older printed CEJJ bulletins, not for this corpus.

---

## Tier 1 — Statutory law

| Source | Status | Format | Language | Verdict |
|---|---|---|---|---|
| **jurisitetunisie.com** | ✅ Live (HTTPS) | HTML, one page per article group | French | **Primary source** |
| **Official IORT code PDFs** (via `africa-laws.org` mirror) | ✅ Live | PDF, text layer | French | **Authoritative baseline** |
| **FAOLEX** (`faolex.fao.org`) | ✅ Live | PDF, text layer | French | Supplementary (JORT extracts) |
| `legislation.tn` | ⚠️ HTTP 503 at audit time | — | FR/AR | Re-check from Tunisia |
| `iort.gov.tn` | ⚠️ Reachable, **not scrapable as built** | WebDev session app | Arabic | See blocker below |
| `droit-afrique.com` | ⚠️ HTTP 403 to this client | PDF | French | Re-check from Tunisia |

### Jurisite Tunisie — the workhorse

`robots.txt` is permissive (`User-agent: * / Allow: /`); only forum paths are disallowed.
Codes live under `/tunisie/codes/<slug>/` with `menu*.html` indexes and `*.htm` content pages.

- **33 codes** listed at [`carte_codes.htm`](https://www.jurisitetunisie.com/carte_codes.htm),
  including the three constitutions (1959, 2014, 2022), Penal, Criminal Procedure, Civil &
  Commercial Procedure, Labour, Personal Status, Commercial Companies, Real Rights, and the
  tax codes.
- **The COC is present but unlinked from that index** — it is at
  `/tunisie/codes/coc/menu.html`. Do not rely on `carte_codes.htm` alone for enumeration.
- Measured content-page counts: `ct` (Labour) 123, `cpp` (Crim. Proc.) 67, `cp` (Penal) 66.
  Extrapolating across 33 codes: **roughly 2,000–3,000 HTML pages** for the whole of Tier 1.
- Articles are cleanly delimited (`Article premier`, `Article 2`, …), which suits
  `LegalArticleChunker` directly.

### Official consolidated baseline

The Imprimerie Officielle's own **COC, 2015 revised edition** — 267 pages, 527,850
characters extracted, **1,481 article markers**, full text layer. This is the consolidated
baseline the JORT amendment stream needs to apply deltas against, and it solves the
"amendments with no base text" problem for the single most important civil code.

### Blocker: the IORT scraper cannot work as written

Two independent problems with `JortIngestionJob`:

1. **`iort.gov.tn` has no HTTPS at all.** Port 443 refuses connections. The default
   `forsalaw.rag.jort.base-url` is `https://www.iort.gov.tn`, which can never connect.
   (`legislation.tn` is likewise HTTP-only.)
2. **The site is a WinDev/WebDev 12 application.** The root serves a meta-refresh into
   `/WD120AWP/WD120Awp.exe/CONNECT/SITEIORT` — a stateful, session-driven app with no
   stable `<a href>` links to JORT issues. `JortHtmlParser.extraireNumeros()` doing a plain
   `RestClient.get()` + HTML parse will not find anything to extract.

**Recommendation:** stop treating IORT as the Tier 1 entry point. Build Tier 1 from Jurisite
plus the official code PDFs, and defer IORT to a later browser-driven job (or a data
agreement with the Imprimerie Officielle).

---

## Tier 2 — Court of Cassation (`cassation.tn`)

A TYPO3 site — genuinely scrapable, unlike IORT. HTTP only (HTTPS misconfigured).
No `robots.txt` is served (403), so no stated crawl restriction; rate-limit politely anyway.

### Inventory by subject matter

Retrieved by POSTing the `فقه القضاء` search form once per category:

| Code | Category | Rulings | Year range |
|---|---|---:|---|
| TF | جزائي (criminal) | 949 | 2005–2024 |
| TH | اجراءات مدنية (civil procedure) | 595 | 2011–2022 |
| TG | اجراءات جزائية (criminal procedure) | 568 | 2016–2022 |
| CR | قرارات الدوائر المجتمعة (joint chambers) | 532 | 1991–2023 |
| AS | تأمين وحوادث مرور (insurance/traffic) | 473 | 2011–2021 |
| TA | مدني عام (civil general) | 459 | 2011–2024 |
| TD | اجتماعي (social/labour) | 457 | 2012–2024 |
| MR | عيني (real rights) | 273 | 2012–2024 |
| TB | تجاري (commercial) | 263 | 2004–2023 |
| LC | أكرية (leases) | 183 | 2012–2021 |
| TC | شخصي (personal status) | 143 | 2012–2020 |
| TI | تحكيم (arbitration) | 124 | 1996–2023 |
| UR | استعجالي (urgent proceedings) | 117 | 2012–2021 |
| VT | بيع (sale) | 93 | 2011–2021 |
| PC | التناسب - الفصل 49 (proportionality) | 47 | 2014–2023 |
| MS | إجراءات جماعية (collective proceedings) | 43 | 2015–2023 |
| TJ | قانون دولي خاص (private int'l law) | 18 | 2015–2021 |
| | **TOTAL (pre-dedup)** | **5,337** | **1991–2024** |

Counts are per-category listings; a ruling may appear under more than one category, so
**deduplicate by decision number** — the true distinct count will be somewhat lower.

### How to fetch

- **Endpoint:** POST to `/فقه-القضاء/?tx_uploadexample_piexample[action]=list&...`
  (URL-encoded), `multipart/form-data`.
- **Search fields:** `[search][shkeyword]`, `[shdocdate1]`, `[shdocdate2]`, `[shdocnum]`,
  `[shtheme]` (the 17 codes above).
- **Required:** the hidden `__referrer[*]` and `__trustedProperties` fields must be copied
  from a freshly fetched form page — TYPO3 HMACs them. No cookies and no JavaScript are
  needed; a plain HTTP client works. A stale token yields HTTP 403.
- **Rulings:** `http://www.cassation.tn/fileadmin/user_upload/<num>.pdf`

### Text-layer verification

Superseded by the full harvest — see "Harvest results" below. **5,339 of 5,340 rulings
(99.98%) carry a usable text layer.** Exactly one is a scan.

---

## ⚠️ Correctness defect: bidi digit reversal in extracted PDF text

**This is the single biggest data-integrity risk in the whole pipeline**, and it is silent —
extraction "succeeds", the chunk looks fine, and the citation is wrong.

Numeric runs embedded in right-to-left Arabic text are emitted in reversed order by PDFBox
(and by pypdf — it is not a library bug but the bidi reordering problem). Verified examples:

| File | True value | Extracted as |
|---|---|---|
| `47234-18.pdf` | case **47234** | `43274` — exact digit reversal |
| `28620-18.pdf` | case **28620** | `82682` |
| `28474.pdf` | date **14/02/2019** | `2019/02/14` — group order flipped |
| `80956.pdf` | date **11/04/2019** | `2019/04/11` — group order flipped |

It is **inconsistent** — `28474`, `80956`, `87942` and `44803` extracted their case numbers
correctly, and `87942`'s spelled-out date (`14 جويلية 2020`) came through intact. So you
cannot detect the problem by spot-checking a few documents, and you cannot correct it with a
blanket "reverse all digits" rule.

**Mitigation — take metadata from HTML, never from the PDF.** The search results table
already gives you, per ruling, a correctly-ordered decision number, a `DD.MM.YYYY` date, and
a subject headnote. Ingest that as the authoritative metadata; use the PDF only for body
text. Then add a validation step: if a case number parsed out of the PDF body disagrees with
the number from the table, flag the chunk rather than trusting either.

The same caution applies to **article numbers inside Arabic statutory text** (`الفصل 242`)
whenever you ingest Arabic PDFs — the identifiers you cite are exactly the tokens most at
risk.

---

## Language coverage gap

Everything reliably available for **Tier 1 is French** — Jurisite is French-only, as are the
IORT code PDFs and the FAOLEX extracts. **Tier 2 is Arabic.** Since the Arabic text is the
legally authoritative version and the French is a translation, a French-only statutory corpus
retrieved against Arabic case law is a real limitation to design around, not a detail.
BGE-M3 handles cross-lingual retrieval, so this is workable — but citations should make the
source language explicit, and Arabic statutory text should be sourced later (IORT or
`legislation.tn`, both currently blocked).

---

## Immediate actions for the codebase

1. **`JortIngestionJob`** — the `https://www.iort.gov.tn` default cannot connect, and the
   WebDev app defeats the HTML-parse approach. Repoint Tier 1 or disable it explicitly.
2. **`JortIngestionJob:122`** — `effectiveDate` is passed `LocalDate.now()`, the ingestion
   date rather than the document's publication date. Backfilling would stamp every historic
   document with today's date and scramble supersession ordering.
3. **New `CassationIngestionJob`** — TYPO3 form POST → metadata table → PDF fetch. Ingest
   metadata and body separately per the mitigation above.
4. **`PdfTextExtractor`** — the 80-chars-per-page heuristic is well calibrated; every real
   document passed and it correctly identifies genuinely scanned input. Keep it, and add
   digit-integrity validation alongside.
5. Seed the **official COC PDF** as the consolidated baseline before enabling any JORT
   amendment stream.

---

## Harvest results (2026-07-26)

Collected with `scripts/harvest-corpus.py` into a staging directory outside the repo.
Raw files are kept immutable and hashed; Markdown for RAG ingestion is a **derived** layer
built from them later, never a replacement — the conversion has known failure modes
(below), and re-deriving must not require re-scraping.

### Tier 2 — Court of Cassation

| Metric | Value |
|---|---|
| Rulings | **5,340** (all distinct decision numbers) |
| Distinct PDFs | 5,334 — 6 files are shared by two decision numbers |
| Size | 1.91 GB · 33,503 pages |
| Text | 52.2 M characters, of which 38.0 M Arabic |
| Date coverage | **1991-10-22 → 2024-11-27**, zero missing dates |
| Download failures | 0 |

**Integrity (`digit_check`)**

| Result | Count | Share |
|---|---:|---:|
| `ok` — official number present in extracted text | 5,218 | 97.7% |
| `absent` — number not found in body | 120 | 2.2% |
| `reversed` — corruption confirmed | **2** | 0.04% |
| No text layer | 1 | 0.02% |

Breakdown of the 120 `absent`: **110** simply never repeat the decision number in the body
(benign — the header carries it as an image); **8** have the digits split by layout
whitespace (benign); **1** has no text layer; **1** is a genuine discrepancy (below).

### Documents to quarantine

| Decision | Issue |
|---|---|
| `6187.13` | Header extracts as `7816`; date `2013` extracts as `3182` |
| `59045.18` | Header extracts as `54095`; date `2018` extracts as `5002` |
| `31643.18` | PDF body reads `31634` — a two-digit transposition. Source of the discrepancy is unclear; could be a typo in the site's own metadata table. Needs a human look. |
| `34721.22` | 10 pages, zero extractable characters — the only true scan in the corpus |

**The corruption is not reordering.** `2013` → `3182` is not a reverse of 2013. These are
font/cmap encoding defects, so the digits are *wrong*, not shuffled — no post-hoc reversal
rule can recover them. This is the concrete justification for the rule below.

> **Metadata comes from the HTML results table. Never from the PDF body.**
> The table gives a correct decision number, a `DD.MM.YYYY` date and a subject headnote for
> every ruling. The PDF supplies body text only.

### Tier 1 — Jurisite

**2,279 pages across 34 codes, 49 MB, 12,592 article headings.** Largest: COC 1,330,
Code de commerce 1,003, Code d'incitation aux investissements 963, Code des collectivités
locales 824, CPCC 738, Code des droits réels 736, Code des sociétés 675, CPP 570,
Code du travail 523, Code pénal 372.

Two crawler defects were found and fixed during collection, both worth knowing if the
crawl is ever rewritten:

- Jurisite mixes relative (`Coc1022.htm`) and absolute (`/tunisie/codes/.../x.htm`) links.
  Filtering hrefs on "contains a slash" silently drops whole sections — Constitution 2014
  collected **1 page instead of 58**. Resolve against the current URL, then filter by
  directory.
- The article-heading count must run on **entity-decoded** text. Jurisite writes
  `Article&nbsp;5`; on raw HTML no `\s` matches, and the Code pénal appears to contain
  25 articles instead of 372.

---

## ⚠️ `LegalArticleChunker` will mis-segment Tier 1 as written

Three defects, found by inspecting the harvested HTML. All three cause **silent** failure —
the chunker falls back to fixed windows and drops article references rather than erroring.

1. **`Article.` with a trailing period is not matched.** The Code du travail writes
   `Article.&nbsp;10&nbsp;:`. The pattern is `(?:Article|ARTICLE|Art\.)\s*(\d…)` — against
   `Article.`, `\s*` meets a period and fails. **523 articles** affected in the labour code
   alone. Fix: `Article\.?`.
2. **`ART.` (uppercase abbreviation) is not matched** — `Article|ARTICLE|Art\.` is
   case-sensitive. Only 9 occurrences, all in the COC, but free to fix alongside (1).
3. **The `(?m)^\s*` line anchor is the dangerous one.** In the source HTML, headings run
   inline mid-paragraph — *"…prévus par la loi. Article 38 (Modifié) - …"*. If the
   HTML→Markdown conversion does not place every heading at the start of its own line, the
   chunker matches **nothing at all** and window-chunks the entire document.

`normaliser()` already folds non-breaking spaces to ordinary ones, which is correct
foresight — but it only helps if conversion turns `&nbsp;` into U+00A0 rather than leaving
the literal string. Requirement on the converter, not on the chunker.

---

## Official code PDFs — africa-laws.org (harvested)

**67 PDFs · 5,293 pages · 88 MB · 10.9 M characters.** 63 carry text layers; **4 need OCR**
(three Arabic IP statutes and, notably, the French *loi n° 2016-48 relative aux banques*,
35 pages). **17 documents are Arabic.** Includes the **Code des douanes** (170 pages), which
Jurisite does not carry at all.

### How complete is Jurisite really? — measured, not assumed

Article-number sets extracted from each official PDF and diffed against the corresponding
Jurisite code:

| Code | Official | Jurisite | Only official | Only Jurisite | Union |
|---|---:|---:|---:|---:|---:|
| coc | 1,404 | 1,320 | **84** | 0 | 1,404 |
| cc (commerce) | 540 | 519 | **26** | 5 | 545 |
| cs (sociétés) | 460 | 475 | 3 | 18 | 478 |
| cpcc | 456 | 458 | 0 | 2 | 458 |
| ct (travail) | 428 | 446 | 0 | 18 | 446 |
| cdr (droits réels) | 405 | 396 | 9 | 0 | 405 |
| cpp | 343 | 341 | 2 | 0 | 343 |
| cp (pénal) | 321 | 321 | 0 | 0 | 321 |
| csp | 213 | 213 | 0 | 0 | 213 |
| cdet | 138 | 151 | 0 | 13 | 151 |
| cdpf | 133 | 132 | 1 | 0 | 133 |
| cde (enfant) | 131 | 122 | 9 | 0 | 131 |
| cirppis | 95 | 97 | 8 | 10 | 105 |
| flocal | 95 | 94 | 1 | 0 | 95 |
| **tva** | 63 | 21 | **42** | 0 | 63 |
| **Total** | | | **185** | | **5,291** |

**Verdict: Jurisite is materially complete for most codes** — typically a 0–9 article gap,
and for `cs`, `ct`, `cdet` it actually holds *more* than the official PDF. Three real
outliers justify using the official texts as primary:

- **COC — 84 articles missing** (spot-checked: arts. 13, 42, 87, 197, 336, 626 all genuinely
  absent from Jurisite, with full substantive text officially).
- **TVA — 42 of 63 missing.** Confirmed real: arts. 35 and 50 absent. The official PDF is
  471 pages against Jurisite's 26 — Jurisite carries only a fragment of this code.
- **Code de commerce — 26 missing.**

> **Methodological warning.** A first pass of this diff reported the Code du travail at
> **17 articles vs 428**, implying a catastrophic 96% gap. That was an artefact of the
> measuring regex, not a real gap — the Code du travail writes `Article. 10` *with a period*,
> which `(?:Article|Art\.)\s*\d` cannot match. With `Articles?\.?` the true figure is 446,
> *more* than the official PDF. **This is the same defect described in the
> `LegalArticleChunker` section below**, and it is the clearest demonstration of why it
> matters: the identical bug, in the chunker, would silently drop every article in that code.
> Several codes also matched two official PDFs each (e.g. a code plus its amending law),
> double-counting until deduplicated to the richest match.

---

## Sources found after the initial audit (not yet harvested)

Both were missed on the first pass — `legislation-securite.tn` because it was probed as
`www.legislation-securite.tn`, which fails DNS. **Always probe the apex domain too.**

### jort.tn — independent JORT mirror

**22,395 issue PDFs, 1957–2026**, ~338,000 OCR-indexed pages, mirrored from the Imprimerie
Officielle and refreshed continuously. Serves HTTPS (unlike `iort.gov.tn`), publishes a
sitemap, and uses clean predictable paths (`/browse/…`, `/view/{collection}/{lang}/{year}/{issue}`).
Both Arabic and French; Arabic is authoritative.

This is the realistic answer to the JORT gap — `iort.gov.tn` remains unscrapable.

**Terms — read before harvesting.** `robots.txt` carries Content-Signal directives:

```
Content-Signal: search=yes, ai-train=no, use=reference
```

- `ai-train=no` — training/fine-tuning is expressly refused, asserted as a reservation of
  rights under Art. 4 of EU Directive 2019/790.
- `ai-input` (the signal that explicitly covers retrieval-augmented generation) is **absent**
  from the line, so by the file's own rule (c) RAG is neither granted nor restricted.
- The operator then adds a block overriding Cloudflare's managed AI blocking: *"Public-domain
  official content — AI crawlers explicitly welcome… we encourage citation in AI-powered
  search"*, with `Allow: /` for ClaudeBot, GPTBot, PerplexityBot and others.
- `Disallow:` `/api/`, auth paths, and every `?q=` / `?page=` / `?year=` permutation.
  `/browse/` and `/view/` are allowed.

Retrieval-with-citation fits their stated intent; training on the corpus does not. Given
they invite contact, ask for sanctioned bulk access rather than scraping 22k PDFs.

### jort.tn — HARVESTED (2000–2026)

| Metric | Value |
|---|---|
| Issues | **5,955** (2,988 FR · 2,969 AR) |
| Pages | **219,760** |
| Text | **737.7 M characters** |
| Size | 11.33 GB |
| Text layer | **5,953 / 5,957 (99.93%)** |
| Runtime | 295 min at 1.5 s spacing |

**This single source is roughly 8× the rest of the corpus combined.** OCR density is healthy
across every era — 2,000–6,700 characters per page — and only 22 issues fall below
300 chars/page. Only 4 lacked a usable text layer, two of which were truncated downloads
(0 pages) rather than scans, and were purged for re-fetch.

**Scope note:** deliberately bounded to 2000–2026. The full range is 12,738 files; at the
*measured* average of 1.95 MB/file (not the 1.23 MB a 10-file sample suggested — sample
small PDFs and you will under-budget by half) the complete backfill to 1957 needs roughly
**13 GB more**.

**Reliability:** ~1% of requests failed with transient TLS errors
(`DECRYPTION_FAILED_OR_BAD_RECORD_MAC`), 74 in total. Failures are never written to the
manifest, so simply re-running the same command retries exactly those and nothing else.

**Use constraint — carried in the collector's docstring, not just here:** `ai-train=no`
applies. This corpus feeds a citation-bearing vector index. Do not train or fine-tune on it.

### legislation-securite.tn — DCAF legal database

WordPress + WPML, run by the Geneva Centre for Security Sector Governance. **The full text is
served as structured JSON over the WordPress REST API** — no HTML parsing, no PDF extraction,
no bidi digit risk.

- `/wp-json/wp/v2/latest-laws` — **3,959 French records, 4,043 Arabic** (Arabic has more, and
  is the authoritative version).
- Rich taxonomies for retrieval filtering: `text-type-categories`, `institution-categories`,
  `status-categories`, `thematic-folders-categories`.
- Text types: Décret 1387 · Décret gouvernemental 527 · Loi 484 · Arrêté 462 · Circulaire 212
  · Décision 209 · Décret présidentiel 158 · Loi organique 128 · Décret-loi 99.
- Institutions: Intérieur 636 · Défense 542 · Présidence du Gouvernement 382 · Présidence de
  la République 265 · Justice 218 · Finances 217 · Affaires locales 178 · ISIE 96.

**Scope caveat:** this is DCAF's *security-sector governance* corpus, not all Tunisian law.
It is nonetheless the only structured source we have for the décret/arrêté/circulaire layer.

**Terms:** `robots.txt` sets `Crawl-delay: 10` — honour it. `/wp-content/uploads/` is
**disallowed**, so the 45,834 media attachments must not be fetched; `/wp-json/` is not
disallowed. The harvester hard-floors the delay at 10 s and paginates with `offset` rather
than `page`, since `?page=` is a disallowed pattern.

### Harvest results — `legislation-securite` (2026-07-26, 14.7 min)

| | FR | AR | Total |
|---|---:|---:|---:|
| Records | 3,959 | 4,043 | **8,002** |
| Usable | 2,901 (73.3%) | 3,277 (81.1%) | **6,178 (77.2%)** |
| Empty (0 chars) | 781 | 764 | 1,545 |
| "not yet translated" notices | 277 | 2 | 279 |
| Characters | 18.0 M | 18.5 M | **36.5 M** |

**Nearly a fifth of the source carries no text.** Those records are flagged
`placeholder: true` in the manifest and must be excluded from ingestion — they are
availability notices, not law.

**Arabic has more usable records than French (3,277 vs 2,901)** and is the authoritative
version. This is currently the only structured Arabic statutory text in the corpus.

Legal status (usable records only):

| FR | n | AR | n |
|---|---:|---|---:|
| en vigueur | 2,189 | ساري المفعول | 2,501 |
| abrogé | 415 | انتهى به العمل | 420 |
| n'est plus en vigueur | 114 | ملغى | 126 |

Text types — FR: Décret 931 · Décret gouvernemental 450 · Loi 381 · Arrêté 280 ·
Circulaire 150 · Décret présidentiel 134. AR: أمر 942 · أمر حكومي 530 · قانون 381 ·
قرار (وزاري) 326 · منشور 219.

**Two traps, both hit during this harvest:**

- **WPML assigns different term IDs per language.** Fetching the taxonomy once without a
  `lang` parameter returns only French terms, and every Arabic record then carries raw
  numeric IDs (`188`) instead of `ساري المفعول` — silently destroying the legal-status
  filter. `legsec_terms()` now queries per language.
- **Do not pair FR and AR statuses by literal translation.** The counts suggest
  `انتهى به العمل` (420) aligns with `abrogé` (415), and `ملغى` (126) with
  `n'est plus en vigueur` (114) — the opposite of what a literal reading suggests. Treat
  each language's taxonomy independently, or resolve pairing through WPML's translation
  links rather than by guessing.

**`status` must become a retrieval filter.** Roughly 15% of usable records are no longer in
force. Answering from repealed law without saying so is worse than not answering.

---

## Sources

- [Jurisite Tunisie — codes index](https://www.jurisitetunisie.com/carte_codes.htm)
- [Jurisite — Code des Obligations et des Contrats](https://www.jurisitetunisie.com/tunisie/codes/coc/menu.html)
- [Court of Cassation — فقه القضاء](http://www.cassation.tn/)
- [Official IORT COC, 2015 edition (mirror)](https://www.africa-laws.org/Tunisia/civil%20law/Code%20des%20obligations%20et%20contrats.pdf)
- [FAOLEX — Tunisia](https://faolex.fao.org/docs/pdf/tun107244.pdf)
- [legislation.tn](http://www.legislation.tn/fr) (503 at audit time)
- [CEJJ — publications catalogue](https://www.cejj-justice.tn/fr/boutique/) (print sales; Tier 2 fallback)
- [ILO NATLEX — Tunisia](https://natlex.ilo.org/)
