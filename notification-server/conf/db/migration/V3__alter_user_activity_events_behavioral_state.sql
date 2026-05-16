-- Switch user_activity_events from per-event schema to behavioral state snapshot schema.
-- DEV/PET project assumption: dropping old columns is acceptable.

ALTER TABLE user_activity_events
  DROP COLUMN IF EXISTS event_id,
  DROP COLUMN IF EXISTS session_id,
  DROP COLUMN IF EXISTS event_type,
  DROP COLUMN IF EXISTS page,
  DROP COLUMN IF EXISTS timestamp,
  DROP COLUMN IF EXISTS device,
  DROP COLUMN IF EXISTS browser,
  DROP COLUMN IF EXISTS scroll_depth,
  DROP COLUMN IF EXISTS location;

ALTER TABLE user_activity_events
  ADD COLUMN IF NOT EXISTS parking_searches INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS slot_views INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS booking_attempts INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS avg_scroll_depth INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_location VARCHAR(255),
  ADD COLUMN IF NOT EXISTS last_activity TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS session_duration INT NOT NULL DEFAULT 0;

DROP INDEX IF EXISTS idx_user_activity_timestamp;

CREATE INDEX IF NOT EXISTS idx_user_activity_last_activity
ON user_activity_events(last_activity);
