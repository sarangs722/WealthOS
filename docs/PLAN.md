# Milestone 1 plan

Historical plan. Milestone 1 was completed; the subsequently authorized Milestone 2 plan is [here](MILESTONE_2_PLAN.md).

1. Establish reproducible Gradle/Android build (minimum API 29, compile/target API 37) and pure JVM finance module.
2. Model bank accounts and append-only dated balance observations. Aggregate latest effective observations with explicit missing/foreign-currency status.
3. Encrypt Room using SQLCipher, wrap its random database key with Android Keystore, exclude device/cloud backups and fail closed on storage errors.
4. Build native Compose Home/Portfolio, add/edit account and balance history, isolated fictional demo, privacy settings and navigation shell.
5. Run financial fixtures, encrypted repository/migration tests, Compose workflows, lint and APK build; launch on an emulator if available.

Only Milestone 1 is in scope. Hilt/WorkManager/network providers are deferred until dependency graph/background work warrants them. Manual constructor injection keeps this slice small and testable. Portable exports, investment models, forecasts and calculators belong to later milestones.
