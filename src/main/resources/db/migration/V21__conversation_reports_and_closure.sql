ALTER TABLE conversations ADD COLUMN closed_at TIMESTAMP;

CREATE TABLE conversation_reports (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(30) NOT NULL,
    details VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    CONSTRAINT conversation_report_reason CHECK (reason IN ('SUSPICIOUS_BEHAVIOR', 'SCAM', 'HARASSMENT', 'OTHER')),
    CONSTRAINT conversation_report_status CHECK (status IN ('OPEN', 'REVIEWED')),
    CONSTRAINT conversation_report_unique_reporter UNIQUE (conversation_id, reporter_id)
);

CREATE INDEX idx_conversation_reports_status_created ON conversation_reports(status, created_at DESC);
