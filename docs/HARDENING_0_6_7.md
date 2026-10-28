# Financial-history restoration review

Bounded first-party review, not an independent security audit. No phone access or new financial features.

1. Compare normal-write invariants with encrypted-backup restoration, especially the snapshot-to-unit-tracking boundary and valuation coverage markers.
2. Reproduce any gaps with authenticated fictional backup payloads; assert rejection before live rows or safety backups change. Verify failures against the existing code before repair.
3. Keep schema 7, encryption/key formats and historical financial inputs intact. Recompute system-owned markers at initial snapshot creation and reject impossible imported markers/dates.
4. Run the full encrypted persistence/backup/migration suite and applicable native workflows, unit tests, builds and lint. Do not change correct financial expectations.
5. Record actual evidence and limitations. Signing/phone acceptance/independent review are separate steps.

## Findings and repairs

- Restore accepted a transaction before the known starting position even though normal entry rejects that history. Restoration now checks financial date and same-day sequence order against the starting position.
- Restore accepted negative/nonexistent valuation markers, or markers referencing events after the valuation date. Markers now resolve to actual eligible event sequences. Per-instrument sequence/date indexes handle backdated entries without assuming sequence numbers follow financial dates or repeatedly scanning the entire ledger.
- Initial snapshot creation trusted caller-supplied coverage markers. The repository now initializes those system-owned markers to zero for the empty ledger, retaining the financial value/date/optional reported figures.

The original three regression probes failed against 0.6.6; their red APK/log are retained. The first repair passed 72 encrypted storage cases. An additional backdated-sequence case covers valid sparse date ordering and an ineligible marker below the numerical maximum. The final 73-case suite passed, along with 129 JVM tests and ten affected native app workflows on API 29. Normal app entry, old backups, new snapshot upgrades, different-key restoration, failed-write rollback and migration checks retain their expected results.

No schema migration, encryption change, recalculation of existing financial records, factual transaction inference or phone access is required. Authenticated encryption was not broken by these findings: the probes deliberately constructed correctly encrypted fictional payloads to test logical validation after decryption. This is not evidence of access to anyone's real vault or a completed independent audit.

[Recorded verification and artifact hashes](verification/history-hardening-067/verification.json). The original failing probes and final successful suite logs are retained. Final build/signature status is recorded in the release notes.
