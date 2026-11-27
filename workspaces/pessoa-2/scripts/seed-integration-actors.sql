\set ON_ERROR_STOP on
\getenv demo_password P2_SMOKE_PASSWORD
\if :{?demo_password}
\else
  \echo Defina P2_SMOKE_PASSWORD antes de executar o seed.
  \quit 1
\endif

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

INSERT INTO identity.users (id, email, password_hash, role, store_id)
VALUES
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa41', 'sprint4-operator@example.invalid',
   public.crypt(:'demo_password', public.gen_salt('bf', 10)), 'OPERATOR', NULL),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa42', 'sprint4-other-store@example.invalid',
   public.crypt(:'demo_password', public.gen_salt('bf', 10)), 'STORE',
   '22222222-2222-4222-8222-222222222223')
ON CONFLICT (id) DO NOTHING;
