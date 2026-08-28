ALTER TABLE user_devices
    ADD COLUMN IF NOT EXISTS utc_offset_minutes INTEGER;
