\set ON_ERROR_STOP on
\getenv demo_password P2_SMOKE_PASSWORD

INSERT INTO identity.users (id, email, password_hash, role, driver_id)
VALUES
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa43', 'sprint4-driver@example.invalid',
   public.crypt(:'demo_password', public.gen_salt('bf', 10)), 'DRIVER',
   '55555555-5555-4555-8555-555555555555'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa44', 'sprint4-other-driver@example.invalid',
   public.crypt(:'demo_password', public.gen_salt('bf', 10)), 'DRIVER',
   '55555555-5555-4555-8555-555555555556')
ON CONFLICT (id) DO NOTHING;
