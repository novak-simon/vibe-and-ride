# Kotlin Server Migration & Backend Consolidation Plan

This plan outlines the transition of the offline Python data pipeline (`vibe-and-ride-utils`) into a unified Kotlin/JVM backend module (`:serverApp`) within the main project.

## User Review Required

> [!IMPORTANT]
> **Audio Analysis Strategy**: The Python analysis uses `BeatNet` and `Librosa`, which are complex to port to native Kotlin. The plan starts with a **Hybrid Approach**: the new Kotlin server will manage the orchestration but call the existing Python analysis scripts as a subprocess.

> [!NOTE]
> **Supabase Access**: You will need to provide the **Supabase Service Role Key** to the new server module via an environment variable (`SUPABASE_SERVICE_KEY`) to bypass RLS for administrative tasks.

## Proposed Changes

### [Backend Infrastructure]

#### [NEW] [:serverApp module](file:///C:/Users/novak/Documents/vibe-and-ride/serverApp)
Create a new Gradle sub-project for the backend.
- **Dependencies**: `:shared`, `ktor-server-core`, `ktor-server-netty`, `supabase-kt`.
- **Purpose**: Host importers, analysis orchestrators, and administrative tasks.

#### [MODIFY] [settings.gradle.kts](file:///C:/Users/novak/Documents/vibe-and-ride/settings.gradle.kts)
Include the new `:serverApp` module.

---

### [Domain Logic Consolidation]

#### [MODIFY] [WorkoutParser.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/workout/WorkoutParser.kt)
Ensure the Kotlin parser is the "Single Source of Truth."
- Sync any missing normalization logic from the Python `workout_parser.py`.
- Ensure `PowerLow/High` and `CadenceLow/High` are fully captured and stored.

---

### [Importer Migration]

#### [NEW] [JamendoImporter.kt](file:///C:/Users/novak/Documents/vibe-and-ride/serverApp/src/main/kotlin/com/novaksimon/vibeandride/server/import/JamendoImporter.kt)
Port the Jamendo API ingestion logic from Python to Kotlin.

#### [NEW] [AudiusImporter.kt](file:///C:/Users/novak/Documents/vibe-and-ride/serverApp/src/main/kotlin/com/novaksimon/vibeandride/server/import/AudiusImporter.kt)
Port the Audius API ingestion logic from Python to Kotlin.

---

### [Analysis Orchestration]

#### [NEW] [AnalysisOrchestrator.kt](file:///C:/Users/novak/Documents/vibe-and-ride/serverApp/src/main/kotlin/com/novaksimon/vibeandride/server/analysis/AnalysisOrchestrator.kt)
- Manage the "Unanalyzed Songs" queue.
- Handle audio downloads to a temporary directory.
- **Hybrid Bridge**: Execute `python main.py analyze --id <song_id>` and parse the result or wait for the Python script to update the DB directly.

## Verification Plan

### Automated Tests
- **Unit Tests**: Verify the Kotlin `WorkoutParser` produces identical results to the Python version on a set of sample `.zwo` files.
- **Integration Tests**: Verify the Kotlin Importers can fetch and correctly map data from Jamendo/Audius APIs.

### Manual Verification
- Run a `serverApp` task to import 10 songs and verify their presence in Supabase.
- Run the analysis orchestrator for one song and verify the Python bridge successfully triggers and updates the database.
- Verify that the `androidApp` build remains unaffected (no server dependencies leaked).
