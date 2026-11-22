-- Event publication registry of Spring Modulith (the outbox for events between modules).
-- An event is stored here in the transaction that publishes it, once per listener.
-- completion_date stays NULL until the listener has finished; incomplete rows are retried.
-- Same layout as schema-postgresql.sql in spring-modulith-events-jdbc 1.2.
CREATE TABLE event_publication
(
    id               UUID                     NOT NULL PRIMARY KEY,
    listener_id      TEXT                     NOT NULL,
    event_type       TEXT                     NOT NULL,
    serialized_event TEXT                     NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
