-- INV-07: status = CANCELLED тоді й лише тоді, коли cancelled_at і cancelled_by_id задані
SELECT id, room_id, author_id, status, cancelled_at, cancelled_by_id
FROM booking
WHERE (status = 'CANCELLED' AND (cancelled_at IS NULL OR cancelled_by_id IS NULL))
   OR (status <> 'CANCELLED' AND (cancelled_at IS NOT NULL OR cancelled_by_id IS NOT NULL));
