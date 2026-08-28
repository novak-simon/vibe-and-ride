-- Hardening RLS Policies for Vibe & Ride

-- 1. profile_song_stats: Remove broad access and add DELETE policy
DROP POLICY IF EXISTS "Enable read access for all users [temporary]" ON "public"."profile_song_stats";

CREATE POLICY "Users can delete their own song stats" ON "public"."profile_song_stats"
  FOR DELETE
  TO "authenticated"
  USING ((auth.uid() = profile_id));

-- 2. workouts: Clean up and restrict insertion
DROP POLICY IF EXISTS "Allow all users i hope to view" ON "public"."workouts";
DROP POLICY IF EXISTS "Enable read access for all users" ON "public"."workouts";
DROP POLICY IF EXISTS "Allow all users to insert" ON "public"."workouts";

CREATE POLICY "Workouts are publicly viewable" ON "public"."workouts"
  FOR SELECT
  TO PUBLIC
  USING (true);

CREATE POLICY "Authenticated users can create workouts" ON "public"."workouts"
  FOR INSERT
  TO "authenticated"
  WITH CHECK (true);

-- 3. songs_depricated: Consolidate redundant policies
DROP POLICY IF EXISTS "Songs are publicly readable" ON "public"."songs_depricated";
DROP POLICY IF EXISTS "Songs are really publicly readable" ON "public"."songs_depricated";

CREATE POLICY "Legacy songs are publicly readable" ON "public"."songs_depricated"
  FOR SELECT
  TO PUBLIC
  USING (true);

-- 4. Ensure metadata is clean (songs, artists, albums)
-- Current policies for these are already:
-- FOR SELECT TO PUBLIC USING (true)
-- This is correct for music metadata.
