CREATE TABLE notifications (

    id BIGSERIAL PRIMARY KEY,

    notification_id VARCHAR(255) NOT NULL,

    user_id VARCHAR(255) NOT NULL,

    event_type VARCHAR(255) NOT NULL,

    message TEXT NOT NULL,

    status VARCHAR(100) NOT NULL,

    retry_count INT DEFAULT 0,

    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id
ON notifications(user_id);

CREATE INDEX idx_notifications_status
ON notifications(status);