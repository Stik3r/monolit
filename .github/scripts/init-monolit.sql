DO $$
BEGIN
  IF EXISTS (
    SELECT FROM pg_roles r
    WHERE rolname = 'monolit' AND (
      NOT rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole
      OR rolreplication OR rolbypassrls
      OR EXISTS (SELECT FROM pg_auth_members WHERE member = r.oid)
    )
  ) OR EXISTS (
    SELECT FROM pg_database
    WHERE datname = 'monolit' AND pg_get_userbyid(datdba) <> 'monolit'
  ) THEN
    RAISE EXCEPTION 'Existing monolit role or database is incompatible';
  END IF;
END $$;
\getenv service_password SPRING_DATASOURCE_PASSWORD
SELECT format('CREATE ROLE monolit LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'service_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'monolit')
\gexec
SELECT NOT EXISTS (SELECT FROM pg_database WHERE datname = 'monolit') AS create_db \gset
\if :create_db
SELECT 'CREATE DATABASE monolit OWNER monolit' \gexec
\connect monolit
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
\endif
