# Data Erasure Policy — Right-to-Erasure vs. Immutable Audit Trail

ForsaLaw is a legal platform: it must honour the **right to erasure** (GDPR Art. 17,
Tunisia INPDP / Law 2004‑63) **and** keep a trustworthy, tamper‑proof **audit trail**.
Those two requirements pull in opposite directions. This document records how we reconcile them.

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

## Notes / open items (DPO to confirm)

- The audit rows themselves may embed personal data (e.g. `ip_address`, `user_agent`). These
  are **retained** as part of the sealed legal record under the erasure exemption for
  **compliance / establishment of legal claims** (GDPR Art. 17(3)(b),(e)). The applicable
  **retention period** and lawful basis must be confirmed and documented by the DPO.
- The anonymization routine (the in‑place `users` update + photo removal) is a data‑layer
  operation on operational tables and is **not** blocked by the audit immutability trigger.
