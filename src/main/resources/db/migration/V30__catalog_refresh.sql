CREATE TABLE catalog_refresh (
 id INTEGER PRIMARY KEY CHECK (id=1), initialized BOOLEAN NOT NULL DEFAULT false,
 status VARCHAR(16) NOT NULL DEFAULT 'IDLE' CHECK(status IN ('IDLE','RUNNING','SUCCEEDED','FAILED')),
 job_id UUID, started_at TIMESTAMPTZ, finished_at TIMESTAMPTZ, heartbeat_at TIMESTAMPTZ,
 pages INTEGER NOT NULL DEFAULT 0, added INTEGER NOT NULL DEFAULT 0,
 updated INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL DEFAULT 0, error TEXT
);
INSERT INTO catalog_refresh(id,initialized) VALUES (1,EXISTS(SELECT 1 FROM items WHERE source='margoworld'));
