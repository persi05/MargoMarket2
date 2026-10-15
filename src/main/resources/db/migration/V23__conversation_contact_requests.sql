ALTER TABLE conversations
    ADD COLUMN contact_request_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN contact_requester_id BIGINT REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE conversations
    ADD CONSTRAINT conversation_contact_request_status
        CHECK (contact_request_status IN ('NONE', 'PENDING', 'ACCEPTED', 'REJECTED'));
