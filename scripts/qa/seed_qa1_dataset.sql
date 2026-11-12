-- Bulk PostgreSQL seeder for QA1 performance benchmarking (200,000 lots, 2,000,000 events, 6+ process levels)
-- Target: PostgreSQL 17 (run against a freshly migrated ThreadTrace database)
-- Usage:
--   psql -h localhost -U threadtrace -d threadtrace -f scripts/qa/seed_qa1_dataset.sql

BEGIN;

INSERT INTO parties (code, name, party_type, country, region, exact_address, worker_count, unit_price_usd, created_at)
VALUES
    ('QA1-MILL-1', 'QA1 Spinning & Fabric Mill', 'FABRIC_MILL', 'Bangladesh', 'Gazipur', 'QA1 Confidential Mill Rd', 800, 11.5000, NOW()),
    ('QA1-FAC-1', 'QA1 Apparel Factory', 'FACTORY', 'Bangladesh', 'Savar', 'QA1 Confidential Factory Rd', 1400, 8.2500, NOW()),
    ('QA1-BRAND-A', 'QA1 Brand A', 'BRAND', 'Sweden', 'Stockholm', 'QA1 Confidential Brand HQ', 120, NULL, NOW())
ON CONFLICT (code) DO NOTHING;

-- Clean up any prior QA1 seeded dataset for repeatable, idempotent execution
DELETE FROM compliance_alerts WHERE lot_id IN (SELECT id FROM lots WHERE style_code LIKE 'QA1-CHAIN-%' OR lot_code IN ('LOT-FAB-A1', 'LOT-SHIP-A1'));
DELETE FROM product_passports WHERE shipment_lot_id IN (SELECT id FROM lots WHERE style_code LIKE 'QA1-CHAIN-%' OR lot_code IN ('LOT-FAB-A1', 'LOT-SHIP-A1'));
DELETE FROM lot_edges WHERE parent_lot_id IN (SELECT id FROM lots WHERE style_code LIKE 'QA1-CHAIN-%' OR lot_code IN ('LOT-FAB-A1', 'LOT-SHIP-A1'))
   OR child_lot_id IN (SELECT id FROM lots WHERE style_code LIKE 'QA1-CHAIN-%' OR lot_code IN ('LOT-FAB-A1', 'LOT-SHIP-A1'));
DELETE FROM lot_events WHERE lot_id IN (SELECT id FROM lots WHERE style_code LIKE 'QA1-CHAIN-%' OR lot_code IN ('LOT-FAB-A1', 'LOT-SHIP-A1'));
DELETE FROM lots WHERE style_code LIKE 'QA1-CHAIN-%' OR lot_code IN ('LOT-FAB-A1', 'LOT-SHIP-A1');


-- 25,000 supply-chain lineages x 8 lots per chain = 200,000 lots
-- Levels 1..7 represent 6 downstream process transitions from FABRIC (LOT-FAB-A1) to SHIPMENT (LOT-SHIP-A1):
--   RAW_FIBER -> FABRIC (LOT-FAB-A1) -> CUT_BUNDLE -> SEWN_GARMENT -> WASHED_GARMENT -> FINISHED_GARMENT -> CARTON -> SHIPMENT (LOT-SHIP-A1)
WITH ref AS (
    SELECT
        (SELECT id FROM parties WHERE code = 'QA1-MILL-1') AS supplier_id,
        (SELECT id FROM parties WHERE code = 'QA1-FAC-1')  AS factory_id
)
INSERT INTO lots (
    lot_code, lot_type, material_type, style_code, brand_code,
    supplier_id, factory_id, quantity, mass_kg, piece_count, unit,
    claimed_attribute, claimed_pct, status, orphan, event_sequence,
    latest_event_hash, created_at, updated_at
)
SELECT
    CASE
        WHEN (g.i / 8) + 1 = 1 AND (g.i % 8) = 1 THEN 'LOT-FAB-A1'
        WHEN (g.i / 8) + 1 = 1 AND (g.i % 8) = 7 THEN 'LOT-SHIP-A1'
        ELSE 'QA1-C' || ((g.i / 8) + 1) || '-L' || (g.i % 8)
    END AS lot_code,
    CASE (g.i % 8)
        WHEN 0 THEN 'RAW_FIBER'
        WHEN 1 THEN 'FABRIC'
        WHEN 2 THEN 'CUT_BUNDLE'
        WHEN 3 THEN 'SEWN_GARMENT'
        WHEN 4 THEN 'WASHED_GARMENT'
        WHEN 5 THEN 'FINISHED_GARMENT'
        WHEN 6 THEN 'CARTON'
        ELSE 'SHIPMENT'
    END AS lot_type,
    'Organic Cotton Jersey' AS material_type,
    'QA1-CHAIN-' || ((g.i / 8) + 1) AS style_code,
    CASE WHEN ((g.i / 8) + 1) % 2 = 1 THEN 'BRAND_A' ELSE 'BRAND_B' END AS brand_code,
    ref.supplier_id,
    ref.factory_id,
    CASE
        WHEN (g.i % 8) <= 1 THEN 100.0000
        WHEN (g.i % 8) = 6 THEN 10.0000
        ELSE 200.0000
    END AS quantity,
    (100.0000 - ((g.i % 8) * 2.0000))::NUMERIC(19, 4) AS mass_kg,
    CASE WHEN (g.i % 8) <= 1 THEN 0 ELSE 200 END AS piece_count,
    CASE
        WHEN (g.i % 8) <= 1 THEN 'KG'
        WHEN (g.i % 8) = 6 THEN 'CARTONS'
        ELSE 'PIECES'
    END AS unit,
    'ORGANIC' AS claimed_attribute,
    80.0000 AS claimed_pct,
    CASE
        WHEN (g.i % 8) = 7 THEN 'COMPLETED'
        WHEN (g.i % 8) = 0 THEN 'CREATED'
        ELSE 'IN_PRODUCTION'
    END AS status,
    FALSE AS orphan,
    10 AS event_sequence,
    md5('qa1-lot-' || g.i) || md5('qa1-lot-ext-' || g.i) AS latest_event_hash,
    NOW() - ((7 - (g.i % 8)) * INTERVAL '1 hour') AS created_at,
    NOW() AS updated_at
FROM generate_series(0, 199999) AS g(i)
CROSS JOIN ref;

ANALYZE lots;

-- 200,000 lots x 10 events per lot = 2,000,000 events
INSERT INTO lot_events (
    event_code, lot_id, event_type, process_step, party_id, production_line_id,
    input_quantity, output_quantity, unit, water_liters, energy_kwh, co2e_kg,
    corrects_event_id, sequence_number, previous_hash, event_hash,
    flagged, flag_reason, notes, recorded_by, occurred_at, recorded_at
)
SELECT
    'QA1-EVT-' || l.id || '-' || s.seq AS event_code,
    l.id AS lot_id,
    CASE
        WHEN s.seq = 1 THEN
            CASE l.lot_type
                WHEN 'RAW_FIBER' THEN 'RECEIVE'
                WHEN 'FABRIC' THEN 'RECEIVE'
                WHEN 'CUT_BUNDLE' THEN 'CUT'
                WHEN 'SEWN_GARMENT' THEN 'SEW'
                WHEN 'WASHED_GARMENT' THEN 'WASH_OR_DYE'
                WHEN 'FINISHED_GARMENT' THEN 'FINISH'
                WHEN 'CARTON' THEN 'PACK'
                ELSE 'SHIP'
            END
        ELSE 'CORRECTION'
    END AS event_type,
    CASE
        WHEN s.seq = 1 THEN
            CASE l.lot_type
                WHEN 'RAW_FIBER' THEN 'receive'
                WHEN 'FABRIC' THEN 'receive'
                WHEN 'CUT_BUNDLE' THEN 'cut'
                WHEN 'SEWN_GARMENT' THEN 'sew'
                WHEN 'WASHED_GARMENT' THEN 'wash_or_dye'
                WHEN 'FINISHED_GARMENT' THEN 'finish'
                WHEN 'CARTON' THEN 'pack'
                ELSE 'ship'
            END
        ELSE 'correction'
    END AS process_step,
    l.factory_id AS party_id,
    NULL AS production_line_id,
    l.quantity AS input_quantity,
    l.quantity AS output_quantity,
    l.unit AS unit,
    CASE WHEN l.lot_type = 'WASHED_GARMENT' AND s.seq = 1 THEN 380.0000 ELSE 1.0000 END AS water_liters,
    2.5000 AS energy_kwh,
    1.2500 AS co2e_kg,
    NULL AS corrects_event_id,
    s.seq AS sequence_number,
    CASE
        WHEN s.seq = 1 THEN repeat('0', 64)
        ELSE md5('qa1-prev-' || l.id || '-' || s.seq) || md5('qa1-prev-ext-' || l.id || '-' || s.seq)
    END AS previous_hash,
    md5('qa1-evt-' || l.id || '-' || s.seq) || md5('qa1-evt-ext-' || l.id || '-' || s.seq) AS event_hash,
    FALSE AS flagged,
    NULL AS flag_reason,
    'QA1 bulk seeded event' AS notes,
    'qa1_seeder' AS recorded_by,
    l.created_at + (s.seq * INTERVAL '1 minute') AS occurred_at,
    NOW() AS recorded_at
FROM lots l
CROSS JOIN generate_series(1, 10) AS s(seq)
WHERE l.style_code LIKE 'QA1-CHAIN-%';

ANALYZE lot_events;

-- 25,000 chains x 7 transitions = 175,000 lineage edges across the 6 process levels
WITH level_map(parent_type, child_type) AS (
    VALUES
        ('RAW_FIBER', 'FABRIC'),
        ('FABRIC', 'CUT_BUNDLE'),
        ('CUT_BUNDLE', 'SEWN_GARMENT'),
        ('SEWN_GARMENT', 'WASHED_GARMENT'),
        ('WASHED_GARMENT', 'FINISHED_GARMENT'),
        ('FINISHED_GARMENT', 'CARTON'),
        ('CARTON', 'SHIPMENT')
)
INSERT INTO lot_edges (
    event_id, parent_lot_id, child_lot_id,
    allocated_quantity, unit, allocated_mass_kg, allocated_pieces, created_at
)
SELECT
    ev.id,
    p.id,
    c.id,
    p.quantity,
    p.unit,
    p.mass_kg,
    p.piece_count,
    c.created_at
FROM level_map m
JOIN lots p ON p.lot_type = m.parent_type AND p.style_code LIKE 'QA1-CHAIN-%'
JOIN lots c ON c.style_code = p.style_code AND c.lot_type = m.child_type
JOIN lot_events ev ON ev.lot_id = c.id AND ev.sequence_number = 1;

COMMIT;

ANALYZE parties;
ANALYZE lots;
ANALYZE lot_events;
ANALYZE lot_edges;
