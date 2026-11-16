-- Інформативний аудит INV-02...INV-05 за параметрами:
-- 1: slot_grid_minutes (ціле число хвилин)
-- 2: work_day_start (time)
-- 3: work_day_end (time)
-- 4: min_duration_minutes (ціле число хвилин)
-- 5: max_duration_minutes (ціле число хвилин)
WITH params AS (
    SELECT CAST(? AS integer) AS slot_grid,
           CAST(? AS time) AS work_day_start,
           CAST(? AS time) AS work_day_end,
           CAST(? AS integer) AS min_duration_minutes,
           CAST(? AS integer) AS max_duration_minutes
)
SELECT b.id, b.room_id, b.author_id, b.start_at, b.end_at, b.status,
       CASE
           WHEN b.start_at >= b.end_at OR CAST(b.start_at AS date) <> CAST(b.end_at AS date) THEN 'INV-02'
           WHEN (EXTRACT(MINUTE FROM b.start_at)::int % p.slot_grid <> 0)
             OR (EXTRACT(SECOND FROM b.start_at)::int <> 0)
             OR (EXTRACT(MINUTE FROM b.end_at)::int % p.slot_grid <> 0)
             OR (EXTRACT(SECOND FROM b.end_at)::int <> 0) THEN 'INV-03'
           WHEN CAST(b.start_at AS time) < p.work_day_start OR CAST(b.end_at AS time) > p.work_day_end THEN 'INV-04'
           WHEN EXTRACT(EPOCH FROM (b.end_at - b.start_at)) / 60 < p.min_duration_minutes
             OR EXTRACT(EPOCH FROM (b.end_at - b.start_at)) / 60 > p.max_duration_minutes THEN 'INV-05'
           ELSE 'UNKNOWN'
       END AS violation_type
FROM booking b
CROSS JOIN params p
WHERE (b.start_at >= b.end_at OR CAST(b.start_at AS date) <> CAST(b.end_at AS date))
   OR ((EXTRACT(MINUTE FROM b.start_at)::int % p.slot_grid <> 0)
    OR (EXTRACT(SECOND FROM b.start_at)::int <> 0)
    OR (EXTRACT(MINUTE FROM b.end_at)::int % p.slot_grid <> 0)
    OR (EXTRACT(SECOND FROM b.end_at)::int <> 0))
   OR (CAST(b.start_at AS time) < p.work_day_start OR CAST(b.end_at AS time) > p.work_day_end)
   OR (EXTRACT(EPOCH FROM (b.end_at - b.start_at)) / 60 < p.min_duration_minutes
    OR EXTRACT(EPOCH FROM (b.end_at - b.start_at)) / 60 > p.max_duration_minutes);
