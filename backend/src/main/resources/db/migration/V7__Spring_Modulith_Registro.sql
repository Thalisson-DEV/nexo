CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    completion_attempts INTEGER DEFAULT 0,
    completion_date TIMESTAMP WITH TIME ZONE,
    event_type VARCHAR(512) NOT NULL,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    listener_id VARCHAR(512) NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    serialized_event TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'
);

CREATE INDEX idx_event_publication_status ON event_publication(status);
CREATE INDEX idx_event_publication_date ON event_publication(publication_date);