# Implementation Plan - Verify and Harden Supabase RLS

This plan addresses Step 13 of the roadmap: verifying and hardening Row Level Security (RLS) policies in Supabase to ensure data security and prevent unauthorized access or spam.

## User Review Required

> [!IMPORTANT]
> - I am removing the `Enable read access for all users [temporary]` policy on `profile_song_stats` which currently allows anyone to read or modify any user's music statistics.
> - I am restricting workout creation to authenticated users only. Currently, anonymous users can insert workouts, which is a potential spam vector.
> - I am adding a `DELETE` policy for `profile_song_stats` to allow users to remove their own data, which was missing.

## Proposed Changes

### Supabase Migrations

#### [NEW] [20260828165525_verify_rls.sql](file:///C:/Users/novak/Documents/vibe-and-ride/supabase/migrations/20260828165525_verify_rls.sql)

This file will contain the following changes:
- **`profile_song_stats`**:
  - Remove the insecure `[temporary]` broad access policy.
  - Add a `DELETE` policy for owners.
- **`workouts`**:
  - Consolidate multiple redundant `SELECT` policies into one `TO PUBLIC` policy.
  - Restrict `INSERT` to authenticated users only (preventing anonymous spam).
  - Keep the `UPDATE` policy for `chosen_count` but ensure it's clean.
- **`songs_depricated`**:
  - Consolidate redundant `SELECT` policies.
- **`profiles`**:
  - Verify existing policies (they are already owner-restricted for all operations).

## Verification Plan

### Automated Tests
- I will check the SQL syntax locally (basic inspection).
- In a real environment, I would run `supabase db lint` or similar, but here I will focus on logical correctness based on the provided schema.

### Manual Verification
- The user should apply this migration to their Supabase project and verify:
  1. Authenticated users can still see their own profiles and stats.
  2. Anonymous users cannot see `profile_song_stats`.
  3. Anonymous users cannot insert workouts.
  4. Authenticated users can still read music metadata (songs, artists, albums).
