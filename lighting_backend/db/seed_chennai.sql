-- Seed fake hotspot clusters in Chennai for demo purposes
-- Requires at least 3 events from 2 distinct users per cluster

-- 1. Koyambedu Bus Terminus (13.0694, 80.1948)
INSERT INTO sos_events (id, user_hash, geom, triggered_at) VALUES
('koy1', 'u1', ST_SetSRID(ST_MakePoint(80.1948, 13.0694), 4326), NOW() - INTERVAL '1 day'),
('koy2', 'u1', ST_SetSRID(ST_MakePoint(80.1948, 13.0695), 4326), NOW() - INTERVAL '5 days'),
('koy3', 'u2', ST_SetSRID(ST_MakePoint(80.1947, 13.0694), 4326), NOW() - INTERVAL '2 days'),
('koy4', 'u3', ST_SetSRID(ST_MakePoint(80.1949, 13.0694), 4326), NOW() - INTERVAL '3 days')
ON CONFLICT (id) DO NOTHING;

-- 2. Egmore Railway Station (13.0782, 80.2605)
INSERT INTO sos_events (id, user_hash, geom, triggered_at) VALUES
('egm1', 'u4', ST_SetSRID(ST_MakePoint(80.2605, 13.0782), 4326), NOW() - INTERVAL '12 hours'),
('egm2', 'u5', ST_SetSRID(ST_MakePoint(80.2604, 13.0783), 4326), NOW() - INTERVAL '15 days'),
('egm3', 'u6', ST_SetSRID(ST_MakePoint(80.2606, 13.0781), 4326), NOW() - INTERVAL '30 days')
ON CONFLICT (id) DO NOTHING;

-- 3. T. Nagar (13.0418, 80.2341)
INSERT INTO sos_events (id, user_hash, geom, triggered_at) VALUES
('tng1', 'u7', ST_SetSRID(ST_MakePoint(80.2341, 13.0418), 4326), NOW() - INTERVAL '100 days'),
('tng2', 'u8', ST_SetSRID(ST_MakePoint(80.2342, 13.0417), 4326), NOW() - INTERVAL '105 days'),
('tng3', 'u9', ST_SetSRID(ST_MakePoint(80.2340, 13.0419), 4326), NOW() - INTERVAL '110 days')
ON CONFLICT (id) DO NOTHING;

-- 4. Anna Nagar West (13.0878, 80.2089)
INSERT INTO sos_events (id, user_hash, geom, triggered_at) VALUES
('ann1', 'u10', ST_SetSRID(ST_MakePoint(80.2089, 13.0878), 4326), NOW() - INTERVAL '2 hours'),
('ann2', 'u11', ST_SetSRID(ST_MakePoint(80.2090, 13.0877), 4326), NOW() - INTERVAL '1 day'),
('ann3', 'u10', ST_SetSRID(ST_MakePoint(80.2088, 13.0879), 4326), NOW() - INTERVAL '3 days')
ON CONFLICT (id) DO NOTHING;
