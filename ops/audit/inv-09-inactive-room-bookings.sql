-- INV-09: незавершені ACTIVE-бронювання в неактивних кімнатах за поточним місцевим часом Києва
SELECT b.id AS booking_id,
       b.room_id,
       r.name AS room_name,
       b.start_at,
       b.end_at,
       b.status,
       r.active AS room_active
FROM booking b
JOIN room r ON b.room_id = r.id
WHERE r.active = false
  AND b.status = 'ACTIVE'
  AND b.end_at > (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Kyiv');
