-- ===================================================================
-- Скрипт підготовки БД RoomBook за таблицею «Підготовка БД» (D-20, A-43)
-- Лише локальна розробка та Testcontainers. Production: init-production.sql.
-- ===================================================================

-- 1. Створення ролей
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'roombook_owner') THEN
        CREATE ROLE roombook_owner WITH LOGIN PASSWORD 'owner_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'roombook_app') THEN
        CREATE ROLE roombook_app WITH LOGIN PASSWORD 'app_pass';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'roombook_backup') THEN
        CREATE ROLE roombook_backup WITH LOGIN PASSWORD 'backup_pass';
    END IF;
END $$;

-- 2. Власник бази та схеми
ALTER DATABASE roombook OWNER TO roombook_owner;
ALTER SCHEMA public OWNER TO roombook_owner;

-- 3. Доступ до бази даних
GRANT CONNECT ON DATABASE roombook TO roombook_owner;
GRANT CONNECT ON DATABASE roombook TO roombook_app;
GRANT CONNECT ON DATABASE roombook TO roombook_backup;

-- 4. Права roombook_backup
GRANT pg_read_all_data TO roombook_backup;

-- 5. Права roombook_app на схему public:
-- USAGE на схему, SELECT/INSERT/UPDATE на таблиці, USAGE/SELECT на послідовності.
-- DELETE та DDL заборонені.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO roombook_app;
GRANT ALL ON SCHEMA public TO roombook_owner;

-- Привілеї за замовчуванням для нових об'єктів, створених roombook_owner
ALTER DEFAULT PRIVILEGES FOR ROLE roombook_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE ON TABLES TO roombook_app;

ALTER DEFAULT PRIVILEGES FOR ROLE roombook_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO roombook_app;

-- 6. Таймаути ролі застосунку (A-43: lock_timeout 5 с, statement_timeout 10 с)
ALTER ROLE roombook_app SET lock_timeout = '5s';
ALTER ROLE roombook_app SET statement_timeout = '10s';
