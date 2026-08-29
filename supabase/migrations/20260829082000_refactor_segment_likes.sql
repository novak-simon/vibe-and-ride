ALTER TABLE public.song_segments DROP COLUMN is_liked;
ALTER TABLE public.profile_song_stats ADD COLUMN liked_segment integer;
