# Data Erasure Policy — Right-to-Erasure vs. Immutable Audit Trail

ForsaLaw is a legal platform: it must honour the **right to erasure** (GDPR Art. 17,
Tunisia INPDP / Law 2004‑63) **and** keep a trustworthy, tamper‑proof **audit trail**.
Those two requirements pull in opposite directions. This document records how we reconcile them.

> **Implementation status.** Enforced in code by
> `userManagement/service/ErasureService.java`, reached through `DELETE /api/users/me`.
>
> This document previously described the policy while the endpoint only set `actif = false`
> — name, first name, email and phone survived a "deleted" account in clear text. The gap
> between the written policy and the code was itself the exposure, so treat any future edit
> here as a change to `ErasureService` too, and to `ErasureServiceTest`, which asserts the
> absence of the data rather than the execution of the code.

## The constraint

`audit_log` is **append‑only / immutable**. Any `UPDATE` or `DELETE` is physically
rejected at the database level by a trigger (`V8__audit_log_immutable.sql`), and there is
no delete endpoint in the API. **We therefore never delete audit rows and never null out
`audit_log.actor_user_id`** — that would be an `UPDATE`, which the trigger forbids.

## The reconciliation

Personal data lives in **operational tables**, chiefly `users`. On an account‑deletion /
erasure request we **anonymize the `users` row in place** rather than deleting it or mutating
the audit log:

| Field (`users`) | After erasure |
|---|---|
| `nom`, `prenom` | replaced with a non‑identifying placeholder (e.g. `« Utilisateur supprimé »`) |
| `email` | replaced with a non‑recoverable, non‑routable placeholder (e.g. `deleted+<id>@invalid`) |
| `telephone` | cleared (`NULL`) |
| `motdepasse` | cleared / set to an unusable value |
| `profile_photo_document_id` + the stored photo | cleared / deleted from the document vault |
| `actif` | set to `false` (account disabled) |

The `users` **row and its `id` are retained**. `audit_log` is left completely untouched;
its `actor_user_id` keeps pointing at the now‑anonymized row. The result:

- **No personal data remains** in the operational store — right‑to‑erasure is satisfied.
- The **audit trail stays intact and immutable** — its integrity is preserved.
- The audit actor is effectively **pseudonymized**: entries still show *that* an action was
  taken by user `X`, but `X` is no longer identifiable from the operational data.

## Why not null `audit_log.actor_user_id`?

Because that is an `UPDATE` on an immutable table (rejected by the V8 trigger). Anonymizing
the **referenced** `users` row achieves the same privacy outcome (the actor is no longer
identifiable) **without** mutating the audit record — the whole point of an immutable log.

## Backups — erasure is not immediate

`scripts/backup-db.sh` keeps **30 days** of full PostgreSQL dumps (`BACKUP_RETENTION_DAYS`).
A dump is a complete copy of the database, personal data included.

**Consequence: an erasure request is not fully satisfied at the moment we anonymize the
`users` row.** The pre-anonymization values survive in every backup taken before it, and
disappear only as those backups age out — **up to 30 days later**.

This is the standard, defensible position (restoring a backup to surgically edit it would
destroy the integrity of the whole dataset, including the sealed audit trail), but it must be
handled explicitly rather than ignored:

- **Tell the data subject.** The erasure acknowledgement should state that residual copies
  persist in backups for up to 30 days and are then destroyed.
- **Never restore a backup to "recover" an erased account.** If a restore happens for
  unrelated reasons, the anonymization must be **re-applied** to any account erased between
  the backup date and the restore. This step is easy to forget and would silently resurrect
  personal data the platform promised to delete.
- **Retention alignment.** `BACKUP_RETENTION_DAYS` is the upper bound on the erasure delay.
  Raising it lengthens the window and must be a deliberate, documented decision.

## Audit chain anchoring

`V10__audit_log_hash_chain.sql` chains every audit row to the previous one, so any edit,
deletion or insertion is detectable via `GET /api/admin/audit-logs/integrity`.

One limit is worth stating plainly: **a chain stored in the same database can be recomputed
in full by anyone with complete access to that database.** The chain becomes genuinely
tamper-evident only once its head is anchored **outside** the database — an offsite copy that
an attacker on the application host cannot rewrite. The backup bucket is the natural place for
that anchor; wiring the periodic export is an open item below.

**Always verify integrity after a restore** (`scripts/restore-db.sh` prints the reminder):
a doctored dump is exactly the attack the chain exists to catch.

## Notes / open items (DPO to confirm)

- The audit rows themselves may embed personal data (e.g. `ip_address`, `user_agent`). These
  are **retained** as part of the sealed legal record under the erasure exemption for
  **compliance / establishment of legal claims** (GDPR Art. 17(3)(b),(e)). The applicable
  **retention period** and lawful basis must be confirmed and documented by the DPO.
- The anonymization routine (the in‑place `users` update + photo removal) is a data‑layer
  operation on operational tables and is **not** blocked by the audit immutability trigger.
- **Offsite backups are still required.** `docker-compose.prod.yml` runs MinIO on the same host
  as PostgreSQL, so losing that host loses the database *and* its backups. Point
  `BACKUP_S3_ENDPOINT` at external storage before treating this as disaster recovery.
- **Chain-head anchoring is not yet automated.** Exporting the current chain head to the backup
  bucket on each run would close the "attacker recomputes the whole chain" gap described above.
