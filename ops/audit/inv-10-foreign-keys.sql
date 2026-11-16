-- INV-10: висячі посилання (room_id, author_id, cancelled_by_id)
SELECT b.id AS booking_id, b.room_id AS target_id, 'MISSING_ROOM' AS violation_type
FROM booking b
LEFT JOIN room r ON b.room_id = r.id
WHERE r.id IS NULL
UNION ALL
SELECT b.id AS booking_id, b.author_id AS target_id, 'MISSING_AUTHOR' AS violation_type
FROM booking b
LEFT JOIN app_user u ON b.author_id = u.id
WHERE u.id IS NULL
UNION ALL
SELECT b.id AS booking_id, b.cancelled_by_id AS target_id, 'MISSING_CANCELLED_BY' AS violation_type
FROM booking b
LEFT JOIN app_user u ON b.cancelled_by_id = u.id
WHERE b.cancelled_by_id IS NOT NULL AND u.id IS NULL;
