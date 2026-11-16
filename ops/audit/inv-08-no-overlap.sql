-- INV-08: відсутність перетинів ACTIVE-бронювань однієї кімнати
SELECT b1.id AS booking_1_id,
       b2.id AS booking_2_id,
       b1.room_id,
       b1.start_at AS b1_start,
       b1.end_at AS b1_end,
       b2.start_at AS b2_start,
       b2.end_at AS b2_end
FROM booking b1
JOIN booking b2 ON b1.room_id = b2.room_id
               AND b1.id < b2.id
WHERE b1.status = 'ACTIVE'
  AND b2.status = 'ACTIVE'
  AND tsrange(b1.start_at, b1.end_at, '[)') && tsrange(b2.start_at, b2.end_at, '[)');
