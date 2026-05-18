CREATE TABLE user_activity_events (
    id BIGSERIAL PRIMARY KEY,

    user_id VARCHAR(255) NOT NULL,

    parking_searches INT DEFAULT 0,
    slot_views INT DEFAULT 0,
    booking_attempts INT DEFAULT 0,
    avg_scroll_depth INT DEFAULT 0,

    location VARCHAR(255),
    last_activity TIMESTAMP,
    session_duration INT DEFAULT 0,

    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_user_activity_user_id
ON user_activity_events(user_id);

CREATE INDEX idx_user_activity_last_activity
ON user_activity_events(last_activity);