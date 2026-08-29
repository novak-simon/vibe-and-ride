CREATE OR REPLACE FUNCTION search_songs_by_embedding(
  target_embedding vector(512),
  target_bpm int,
  min_limit int,
  max_limit int
)
RETURNS SETOF songs
LANGUAGE plpgsql
AS $$
DECLARE
  bpm_diff int;
  found_count int;
BEGIN
  -- Try increasing BPM ranges: 2, 5, 10, 20
  FOR bpm_diff IN 2, 5, 10, 20 LOOP
    SELECT COUNT(DISTINCT s.id) INTO found_count
    FROM songs s
    JOIN song_segments ss ON s.id = ss.song_id
    WHERE s.bpm BETWEEN (target_bpm - bpm_diff) AND (target_bpm + bpm_diff);

    IF found_count >= min_limit THEN
      RETURN QUERY
      SELECT s.*
      FROM songs s
      JOIN (
        SELECT song_id, MIN(embedding <=> target_embedding) as min_dist
        FROM song_segments
        WHERE song_id IN (
            SELECT id FROM songs
            WHERE bpm BETWEEN (target_bpm - bpm_diff) AND (target_bpm + bpm_diff)
        )
        GROUP BY song_id
      ) ss ON s.id = ss.song_id
      ORDER BY ss.min_dist ASC
      LIMIT max_limit;
      RETURN;
    END IF;
  END LOOP;

  -- Fallback: best matches overall
  RETURN QUERY
  SELECT s.*
  FROM songs s
  JOIN (
    SELECT song_id, MIN(embedding <=> target_embedding) as min_dist
    FROM song_segments
    GROUP BY song_id
  ) ss ON s.id = ss.song_id
  ORDER BY ss.min_dist ASC
  LIMIT max_limit;
END;
$$;
