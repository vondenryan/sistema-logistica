\set ON_ERROR_STOP on
\getenv demo_password P2_SMOKE_PASSWORD
\getenv driver_id INTEGRATION_DRIVER_ID
INSERT INTO identity.users (id, email, password_hash, role, driver_id)
VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa45', 'sprint4-journey-driver@example.invalid',
        public.crypt(:'demo_password', public.gen_salt('bf', 10)), 'DRIVER', :'driver_id');
