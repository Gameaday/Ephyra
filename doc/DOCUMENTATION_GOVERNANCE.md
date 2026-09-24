# Documentation Governance

> **Status:** binding repository policy for the reconstruction program.

## Authority order

When documents disagree, authority is:

1. `ROADMAP.md` — program scope, phase order, and immediate queue.
2. `doc/REBUILD_PROGRAM.md` — normative architecture and delivery rules.
3. `doc/REBUILD_EXECUTION_GUIDE.md` — how to execute tasks.
4. `doc/REBUILD_STATUS.md` — current evidence and task state.
5. `doc/adr/` — accepted architectural decisions.
6. `doc/CACHE_POLICY.md` and `doc/SOURCE_DISCOVERY_ARCHITECTURE.md` — current subsystem contracts.
7. Other documents — non-authoritative context only.

A lower-authority document must not contradict a higher-authority document. If it does, the contradiction is a documentation defect.

## Document states

- **CURRENT:** linked from the roadmap or an accepted ADR, actively maintained, and contains no known false completion claims.
- **HISTORICAL:** retained for archaeology, clearly labeled, and not linked as current requirements.
- **ARCHIVED:** preserved in Git history or a release branch; no longer present in the working tree when it would compete with current guidance.
- **DELETED:** intentionally removed from the current repository; the commit history and a replacement decision remain the record.

There is no unlimited “documentation backlog.” A document is either maintained, clearly historical, archived, or deleted.

## What is deleted

Delete a document when one or more of these are true:

- it claims the legacy architecture is complete when the reconstruction explicitly disproves that claim;
- it describes a temporary implementation as the target architecture;
- it duplicates a current contract and can drift;
- it is a completed handoff/checklist with no active owner or next task;
- it contains operational instructions that contradict the execution guide;
- its only unique value is historical and Git already preserves it.

Do not delete a document merely because it is inconvenient or unfinished. Move the unique facts into a current contract first, update links, verify the replacement, then delete the old file in a separate, explicit commit.

## What is retained

Retain only:

- the current roadmap and program contracts;
- current subsystem policies;
- accepted ADRs;
- the status ledger and execution guide;
- release/security/migration evidence required for auditability.

Historical evidence belongs in the commit that introduced it. A long-lived “old plan” file is not an archive.

## Change protocol

1. Update the current contract.
2. Update the status ledger and links.
3. Search the repository for references to the old document.
4. Run the documentation/link gate.
5. Delete or mark historical in a separate commit.
6. Never combine documentation deletion with unrelated production changes.

## Definition of documentation done

- The roadmap links to every current contract.
- Every current document has an owner and update rule.
- No current document claims completion without evidence level and build context.
- No stale document is linked as a current authority.
- Deleted-document history is recoverable from Git.
- A new contributor can identify the current source, cache, architecture, and execution rules without reading historical files.
