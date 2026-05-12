CREATE TABLE user_activity_events (
    id BIGSERIAL PRIMARY KEY,

    event_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,

    event_type VARCHAR(100) NOT NULL,
    page VARCHAR(255),

    timestamp TIMESTAMP NOT NULL,

    device VARCHAR(100),
    browser VARCHAR(100),

    scroll_depth INT,
    location VARCHAR(255),

    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_user_activity_user_id
ON user_activity_events(user_id);

CREATE INDEX idx_user_activity_timestamp
ON user_activity_events(timestamp);