# MusicProvider State Audit (Post-MusicState Extraction)

This document records the current state and structure of `MusicProvider` following the incremental extraction of its session state into the pure `MusicState` model (Tasks 10–21).

## 1. Classification of Remaining Fields

| Field | Type | Classification | Purpose |
| :--- | :--- | :--- | :--- |
| `uiState` | `MutableStateFlow<MusicUiState>` | **E. UI coupling** | Updates the observable UI state. |
| `service` | `ProfileMusicService` | **F. service coupling** | Dependency for playback and profile operations. |
| `recommender` | `RecommendationEngine` | **F. service coupling** | Dependency for song recommendations. |
| `activePrecomputations` | `MutableSet<Int>` | **B. transient orchestration** | Deduplicates in-flight recommendation tasks. |
| `statsScope` | `CoroutineScope` | **D. concurrency infrastructure** | Scope for IO-bound statistics operations. |
| `recommendScope` | `CoroutineScope` | **D. concurrency infrastructure** | Scope for CPU-bound recommendations. |
| `waitScope` | `CoroutineScope` | **D. concurrency infrastructure** | Scope for UI/Main thread coordination. |
| `recommendSemaphore` | `Semaphore` | **D. concurrency infrastructure** | Limits parallel recommendation requests. |
| `_state` | `MutableStateFlow<MusicState>` | **A. Music/session state** | Source of truth for pure session data. |
| `state` | `MusicState` (delegated) | **A. Music/session state** | Accessor for the current immutable state. |

## 2. Classification of Methods

| Method | Classification | Responsibilities |
| :--- | :--- | :--- |
| `init` | **G. lifecycle**, **F. UI sync** | Collects playback state for UI. |
| `workoutSelected` | **A. command**, **D. mutation**, **B. rec orchestration** | Resets session, prepares buffer, starts precompute. |
| `play` | **A. command**, **C. playback orchestration**, **B. rec orchestration** | Logic for thresholds, preloading, and transition triggers. |
| `incrementStats*` | **E. statistics** | Records track arrival and leave events. |
| `nextTrack` | **C. playback orchestration**, **D. mutation**, **F. UI sync** | Executes crossfades and updates cursor state. |
| `skip`, `pause`, `play` | **A. public command** | Basic playback controls. |
| `songLiked` | **A. command**, **E. statistics** | Records user preference. |
| `precompute*` | **B. rec orchestration**, **D. mutation** | Triggers and handles recommendation results. |

## 3. Current Responsibilities of MusicProvider

*   **Recommendation Orchestration**: Deciding when to request songs and managing the results buffer.
*   **Playback Decision Logic**: Detecting time thresholds (30s for preload, 1s for transition) and choosing the next song.
*   **State Cursor Management**: Tracking progress through intervals and songs (`songIndex`, `currentIntervalIndex`).
*   **Infrastructure Glue**: Connecting coroutine scopes, semaphores, and services to the UI and domain data.
*   **Event Sourcing**: Triggering statistics and database updates based on playback events.

## 4. Future MusicEngine Candidates

Responsibilities intended for extraction into `MusicEngine`:
*   **Session Policy**: Logic for "Next Song" and "Preload Song" based on current state.
*   **Reaction Logic**: How to handle interval or cadence changes logically.
*   **Command Processing**: High-level `play`/`pause`/`skip` logic before it reaches implementation services.
*   **Domain Event Emission**: Emitting signals for stats/database instead of calling them directly.

## 5. Remaining Dependencies and Coupling

*   **Services**: Directly depends on `ProfileMusicService` and `RecommendationEngine`.
*   **Repositories**: Directly depends on `ProfileRepository` and `SongRepository` (via constructor).
*   **Database**: Directly calls Supabase-backed utility functions for statistics.
*   **Concurrency**: Manages its own internal threading/dispatching strategy.

## 6. Recommended First MusicEngine Extraction

**Preload and Fallback Selection Logic**: 
Extract the logic from `play()` that calculates thresholds and selects the `songToPreload` (including fallback to `futureIntervalSong`). This is pure domain logic that can be tested independently of ExoPlayer or Supabase.
