-- INV-14: у системі є щонайменше один активний ADMIN
SELECT count(*) AS active_admin_count
FROM app_user
WHERE role = 'ADMIN' AND enabled = true
HAVING count(*) < 1;
