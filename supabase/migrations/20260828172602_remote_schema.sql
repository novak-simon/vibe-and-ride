SET local check_function_bodies = off;

REVOKE ALL ON FUNCTION "public"."rls_auto_enable"() FROM "anon";

CREATE EXTENSION "vector" SCHEMA "public";

CREATE TABLE "public"."song_segments" (
  "id"              bigint                   GENERATED ALWAYS AS IDENTITY NOT NULL,
  "song_id"         bigint                   NOT NULL,
  "segment_index"   integer                  NOT NULL,
  "start_time"      real                     NOT NULL,
  "end_time"        real                     NOT NULL,
  "label"           text,
  "relative_energy" real,
  "loudness_lufs"   real,
  "embedding"       public.vector(512),
  "created_at"      timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT "song_segments_pkey" PRIMARY KEY (id),
  CONSTRAINT "song_segments_song_id_segment_index_key" UNIQUE (song_id, segment_index)
);

ALTER TABLE "public"."song_segments"
  ENABLE ROW LEVEL SECURITY;

ALTER TABLE "public"."song_segments"
  ADD COLUMN "duration" real GENERATED ALWAYS AS ((end_time - start_time)) STORED;

ALTER TABLE "public"."song_segments"
  ADD CONSTRAINT "song_segments_song_id_fkey" FOREIGN KEY (song_id) REFERENCES public.songs(id) ON UPDATE CASCADE ON DELETE CASCADE;

CREATE INDEX song_segments_embedding_hnsw_idx ON public.song_segments USING hnsw (embedding public.vector_cosine_ops);

CREATE INDEX song_segments_song_id_idx ON public.song_segments USING btree (song_id);

CREATE POLICY "Enable read access for all users" ON "public"."song_segments"
  FOR SELECT
  TO PUBLIC
  USING (true);

COMMENT ON COLUMN "public"."song_segments"."duration" IS 'Computed duration of the segment in seconds.';

COMMENT ON COLUMN "public"."song_segments"."embedding" IS '512-dimensional OpenL3 music embedding vector representing the audio characteristics of this segment.';

COMMENT ON COLUMN "public"."song_segments"."end_time" IS 'End time of the segment in seconds.';

COMMENT ON COLUMN "public"."song_segments"."label" IS 'Structural label (e.g. intro, verse, chorus, drop, outro, or section_X).';

COMMENT ON COLUMN "public"."song_segments"."loudness_lufs" IS 'Objective audio loudness of this section in dBFS / LUFS (e.g. -14.0 dB).';

COMMENT ON COLUMN "public"."song_segments"."relative_energy" IS 'Intra-track dynamic intensity relative to the song peak [0.0 - 1.0].';

COMMENT ON COLUMN "public"."song_segments"."segment_index" IS '0-indexed sequence order of the segment within the song.';

COMMENT ON COLUMN "public"."song_segments"."song_id" IS 'Foreign key referencing public.songs(id) to quickly retrieve all segments for a given song.';

COMMENT ON COLUMN "public"."song_segments"."start_time" IS 'Start time of the segment in seconds.';

COMMENT ON EXTENSION "vector" IS 'vector data type and ivfflat and hnsw access methods';

COMMENT ON TABLE "public"."song_segments" IS 'Stores structural musical segments (intro, chorus, drop, verse, etc.) and their OpenL3 feature embeddings for songs.';

GRANT DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE "public"."song_segments" TO "anon", "authenticated", "postgres", "service_role";

