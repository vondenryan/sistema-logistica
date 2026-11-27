ALTER TABLE notifications.notification_outbox ALTER COLUMN recipient_email DROP NOT NULL;
ALTER TABLE notifications.notification_outbox ALTER COLUMN payload_hash DROP NOT NULL;
