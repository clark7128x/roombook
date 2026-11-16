-- Deterministic A-51 data: 50 rooms, 300 users, 1,000,000 bookings, 70% ACTIVE.
-- Dedicated disposable database only. Password hashes retain the bootstrap bcrypt cost 12.
INSERT INTO room(name,name_key,floor,capacity,equipment,active)
SELECT 'Кімната ' || lpad(n::text,3,'0'), 'кімната ' || lpad(n::text,3,'0'), 1+(n%3), 4+(n%17), 'Екран', true FROM generate_series(1,50) n;
INSERT INTO app_user(username,username_key,display_name,password_hash,role,enabled)
SELECT 'load' || lpad(n::text,3,'0'), 'load' || lpad(n::text,3,'0'), 'Працівник ' || n,
       (SELECT password_hash FROM app_user ORDER BY id LIMIT 1), 'EMPLOYEE', true FROM generate_series(1,299) n;
WITH ids AS (SELECT (SELECT array_agg(id ORDER BY id) FROM room) rooms, (SELECT array_agg(id ORDER BY id) FROM app_user) users),
data AS (
 SELECT g, rooms[1+((g%500)/10)::int] room_id, users[1+(g%300)::int] author_id,
   ((CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Kyiv')::date - 1909 + (g/500)::int)::timestamp + interval '8 hours'
      + (ARRAY[0,15,45,90,150,195,225,285,330,360])[1+(g%10)::int]*interval '1 minute' start_at,
   CASE WHEN g%10=9 AND (g/10)%100=0 THEN 240 ELSE (ARRAY[15,30,45,60,45,30,60,45,30,90])[1+(g%10)::int] END minutes
 FROM generate_series(0,999999) g CROSS JOIN ids
)
INSERT INTO booking(room_id,author_id,title,start_at,end_at,status,created_at,cancelled_at,cancelled_by_id)
SELECT room_id,author_id,'Зустріч ' || g,start_at,start_at+minutes*interval '1 minute',
 CASE WHEN g%10<3 THEN 'CANCELLED' ELSE 'ACTIVE' END,
 least(CURRENT_TIMESTAMP-interval '1 day',(start_at AT TIME ZONE 'Europe/Kyiv')-interval '7 days'),
 CASE WHEN g%10<3 THEN least(CURRENT_TIMESTAMP,(start_at AT TIME ZONE 'Europe/Kyiv')-interval '1 day') END,
 CASE WHEN g%10<3 THEN author_id END FROM data;
ANALYZE room;
ANALYZE app_user;
ANALYZE booking;
