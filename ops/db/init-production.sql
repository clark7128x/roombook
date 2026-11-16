-- psql only, run as the database administrator against an existing roombook DB.
-- Secrets come from the psql process environment; never use ops/db/init.sql in production.
\set ON_ERROR_STOP on
\getenv owner_password ROOMBOOK_OWNER_PASSWORD
\getenv app_password ROOMBOOK_APP_PASSWORD
\getenv backup_password ROOMBOOK_BACKUP_PASSWORD
SELECT 1 / (least(length(:'owner_password'), length(:'app_password'), length(:'backup_password')) > 0)::int;
SELECT 'CREATE ROLE roombook_owner LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'roombook_owner') \gexec
SELECT 'CREATE ROLE roombook_app LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'roombook_app') \gexec
SELECT 'CREATE ROLE roombook_backup LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'roombook_backup') \gexec
SELECT format('ALTER ROLE roombook_owner PASSWORD %L', :'owner_password') \gexec
SELECT format('ALTER ROLE roombook_app PASSWORD %L', :'app_password') \gexec
SELECT format('ALTER ROLE roombook_backup PASSWORD %L', :'backup_password') \gexec
ALTER DATABASE roombook OWNER TO roombook_owner;
ALTER SCHEMA public OWNER TO roombook_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE roombook TO roombook_owner, roombook_app, roombook_backup;
GRANT USAGE ON SCHEMA public TO roombook_app;
GRANT ALL ON SCHEMA public TO roombook_owner;
GRANT pg_read_all_data TO roombook_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE roombook_owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO roombook_app;
ALTER DEFAULT PRIVILEGES FOR ROLE roombook_owner IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO roombook_app;
ALTER ROLE roombook_app SET lock_timeout = '5s';
ALTER ROLE roombook_app SET statement_timeout = '10s';
