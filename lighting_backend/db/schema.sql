-- Enable PostGIS extension if not exists
CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. Table: sos_events
CREATE TABLE IF NOT EXISTS sos_events (
    id TEXT PRIMARY KEY,
    user_hash TEXT NOT NULL,
    geom GEOGRAPHY(Point,4326) NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL,
    is_false_alarm BOOLEAN DEFAULT FALSE
);

-- Index for spatial queries
CREATE INDEX IF NOT EXISTS sos_events_geom_idx ON sos_events USING GIST (geom);
-- Index for user deduplication lookups
CREATE INDEX IF NOT EXISTS sos_events_user_time_idx ON sos_events (user_hash, triggered_at);

-- 2. View: sos_hotspots
CREATE OR REPLACE VIEW sos_hotspots AS
WITH cells AS (
    SELECT 
        id,
        ST_AsText(ST_SnapToGrid(geom::geometry, 0.0014)) AS cid,
        user_hash,
        triggered_at,
        geom
    FROM sos_events
    WHERE NOT is_false_alarm
)
SELECT 
    cid,
    COUNT(*) AS alert_count,
    COUNT(DISTINCT user_hash) AS unique_users,
    MAX(triggered_at) AS last_alert_at,
    SUM(CASE WHEN EXTRACT(HOUR FROM triggered_at AT TIME ZONE 'UTC') >= 22 
               OR EXTRACT(HOUR FROM triggered_at AT TIME ZONE 'UTC') < 5 THEN 1 ELSE 0 END) AS night_count,
    -- Simple centroid of the snapped geometries
    ST_Centroid(ST_Collect(geom::geometry))::geography AS center
FROM cells
GROUP BY cid;
