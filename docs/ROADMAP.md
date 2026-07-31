# ForsaLaw — Architecture Roadmap (Remaining 40%)

Finalized architecture roadmap for taking ForsaLaw from a hardened, functional demo to a
feature-complete, production-ready LegalTech SaaS. Incorporates multi-instance scaling
constraints, legal compliance, data-migration realities, and AI optimization.

## Completed (Phases 1–5)

Delivered on their respective `feature/phase*` branches:

1. **Security & config hardening** — fail-fast secrets, CORS, WhatsApp bridge auth, document upload MIME/extension validation.
2. **Data integrity** — Flyway baseline adoption (`V0`/`V1`/`V2`), auth normalization fix, password policy.
3. **Frontend security** — JWT expiry auto-logout, i18n lazy-loading, WS token moved to the STOMP CONNECT frame.
4. **Testing baseline** — first unit + web-slice tests (AuthService, DocumentFileValidator, RDV security).
5. **Infrastructure & docs** — multipart limits, mail config, consolidated `.env.example`, `CONTRIBUTING.md`.

---

## Phase 6: CI/CD & Testing Foundation

Automated checks are foundational. Put this in place before touching complex logic.

- [ ] **CI/CD Pipeline:** GitHub Actions workflow running `mvn test`, `npm run build`, and SonarCloud on every PR.
- [ ] **TestContainers Setup:** Boot a real PostgreSQL container for `@SpringBootTest` integration tests, starting with the booking/OffsetDateTime flow.

## Phase 7: Core Tech Debt & Time Safety

- [ ] **`OffsetDateTime` Refactor:** Migrate from `LocalDateTime`. **CRITICAL:** DB migration needs `AT TIME ZONE 'Africa/Tunis'`. The frontend wire format will change, so the React components and `AuthExceptionHandler` must be updated to handle the new ISO-8601 offset strings.
- [ ] **ShedLock Integration:** Prevent duplicate email reminders. Requires a dedicated Flyway migration to create the `shedlock` table.
- [ ] **OAuth2 & JWT Security Fix:** Move the JWT out of `localStorage` (XSS risk) and into an `httpOnly` cookie. Implement `CookieBasedAuthorizationRequestRepository` to keep Google Login working under `STATELESS` sessions. **CRITICAL:** Re-enable CSRF protection (since `.csrf.disable()` is only safe when using Authorization headers). Add `SameSite=Strict/Lax` and a CSRF token for state-changing requests.
- [x] **Flyway Finalization:** The 3 runtime DDL patchers are gone, each replaced by a migration (numbered V5/V6/V7, not V3/V4/V5 as originally planned): `DocumentMetadataContexteCheckPatcher` → `V5__document_metadata_contexte_check`, `MessengerMessageReceiptBackfill` → `V6__messenger_receipt_backfill`, `ForsaLawApplication.databaseFix` → `V7__drop_document_access_log_action_check`. No Java code executes DDL at startup any more.
- [ ] **Admin Bootstrap:** Add a seed migration (or one-shot CLI) to safely create the first Admin user.
- [ ] **Audit Immutability vs Erasure:** Enforce append-only rules for the audit trail, but define the GDPR/INPDP right-to-erasure reconciliation (e.g., erase PII from operational tables but retain a pseudonymized audit record).

## Phase 8: Storage, Scaling & Deployment Architecture

- [ ] **Object Storage Migration (S3/MinIO):** Move off local disk. This involves a `V6` database migration to rewrite `chemin_fichier` for Documents, Messenger Attachments, AND Reclamation Attachments. Crucially, rewrite the Java service layer (`DocumentService`, `SignatureService`) to use the S3 client instead of `Paths.get()`.
- [ ] **External STOMP Broker:** Replace Spring's in-memory broker with a RabbitMQ or Redis STOMP relay.
- [ ] **Dockerization:** Add the production Dockerfile for the Spring Boot backend, and a multi-stage Nginx Dockerfile for the Vite frontend.
- [ ] **Load Balancer & Routing:** Define the deployment target. Configure the load balancer with sticky sessions (required for SockJS fallback transports) and TLS termination.
- [ ] **Secrets Management:** Move beyond `.env` for production (HashiCorp Vault or Cloud Secret Manager).

## Phase 9: Legal Compliance, Privacy & DR Gate

- [ ] **Disaster Recovery:** Implement automated PostgreSQL backups + tested restore procedures.
- [ ] **Encryption at Rest:** Enable encryption for the PostgreSQL volume and the S3/MinIO bucket.
- [ ] **AI Privacy Strategy:** Secure a zero-retention/no-training Data Processing Agreement (DPA) with the LLM provider, or self-host an embedding model.
- [ ] **Disclaimers & Consent:** Add explicit UI consent for AI processing and "Not Legal Advice" hallucination disclaimers to protect against liability.

## Phase 10: AI RAG Foundation (Ingestion)

- [ ] **Corpus Operational Sourcing:** Define how the Tunisian legal codes are acquired, loaded into the system, and kept up to date as laws change.
- [ ] **Embedding Model Selection:** Pick a model that excels in Arabic and French (e.g., Mistral or BGE-m3).
- [ ] **`pgvector` Schema Fixes:** Re-write the migration to match the chosen model's dimensions. Replace the `IVFFlat` (`lists=1`) no-op index with a proper HNSW index.
- [ ] **Structural Chunking:** Utilize the existing `article_reference` and `code_name` columns to chunk by legal article structure for superior retrieval.
- [ ] **AI Eval Harness:** Create a small, grounded test set to measure retrieval quality.

## Phase 11: AI RAG Execution (Retrieval) & Observability

- [ ] **Similarity Search & Prompting:** Build the retrieval pipeline and implement prompt-injection defenses.
- [ ] **SSE Streaming Endpoint:** Build the `/api/ai/chat` endpoint using Server-Sent Events (SSE) and connect it natively to the `AiSanctumPage` UI for a real-time typing effect.
- [ ] **Financial Circuit Breakers:** Add hard per-user token budgets and global monthly spend caps to prevent token exhaustion / billing attacks.
- [ ] **AI Observability:** Add Sentry (error tracking) and token/cost dashboards alongside the `/api/ai/chat` endpoint launch.
- [ ] **Global Rate Limiting:** Add Bucket4j rate limiting to auth, password-reset, and the public avocat list.

## Phase 12: Features & Final Prod Hardening

- [ ] **Missing Features:** Build Forum Moderation (admin deletion) and Secure Document Sharing (Avocat ↔ Client).
- [ ] **Transactional Email:** Move off Gmail SMTP. Use AWS SES or SendGrid with proper SPF/DKIM/DMARC records.
- [ ] **Security Headers & Scanning:** Add HSTS/CSP headers, and integrate Dependabot / OWASP Dependency-Check.
- [x] **`JPA_DDL_AUTO=validate`:** `validate` is now the default in `application.properties` (no longer `update`). Verified against a *pristine* database — the 15 migrations V0→V14 applied to an empty schema, then validated with zero drift. Validating against a database that had run under `update` would have proven nothing, since Hibernate may have silently patched it over time.
- [ ] **WhatsApp Bridge Review:** Acknowledge the account-ban risk of using the unofficial `whatsapp-web.js` library, or plan a migration to the official Cloud API.
