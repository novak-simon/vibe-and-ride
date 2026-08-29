CREATE OR REPLACE FUNCTION get_song_second_segment_embedding(p_song_id bigint)
RETURNS public.vector(512)
LANGUAGE sql
STABLE
AS $$
  SELECT embedding
  FROM public.song_segments
  WHERE song_id = p_song_id AND segment_index = 1
  LIMIT 1;
$$;
