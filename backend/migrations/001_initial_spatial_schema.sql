CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";

DO $$ BEGIN
    CREATE TYPE polymer_category AS ENUM (
        'PET_CLEAR', 'PET_AMBER_GREEN', 'HDPE_BLOW_MOULD', 'HDPE_INJECTION', 'ALUMINIUM_UBC', 'K4_CARDBOARD'
    );
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE verification_stage AS ENUM (
        'ORIGIN_MANIFEST', 'RECLAIMER_COLLECTED', 'BBC_RECONCILED', 'PRO_AUDITED'
    );
EXCEPTION WHEN duplicate_object THEN null;
END $$;

CREATE TABLE IF NOT EXISTS household_manifests (
    manifest_id VARCHAR(64) PRIMARY KEY,
    source_channel VARCHAR(32) NOT NULL DEFAULT 'WHATSAPP',
    msisdn_hash CHAR(64) NOT NULL,
    declared_material polymer_category NOT NULL,
    estimated_volume_litres NUMERIC(6, 2) NOT NULL,
    masked_location GEOMETRY(Point, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transaction_handoff_logs (
    transaction_id VARCHAR(64) PRIMARY KEY,
    manifest_id VARCHAR(64),
    reclaimer_id_hash CHAR(64) NOT NULL,
    scale_hardware_mac CHAR(17) NOT NULL,
    polymer_type polymer_category NOT NULL,
    gross_weight_kg NUMERIC(6, 2) NOT NULL,
    tare_weight_kg NUMERIC(6, 2) NOT NULL,
    net_weight_kg NUMERIC(6, 2) GENERATED ALWAYS AS (gross_weight_kg - tare_weight_kg) STORED,
    pickup_point GEOMETRY(Point, 4326) NOT NULL,
    verification_state verification_stage NOT NULL DEFAULT 'RECLAIMER_COLLECTED',
    device_recorded_at TIMESTAMPTZ NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    transaction_hmac CHAR(64) NOT NULL
);

-- POPIA Privacy centroid snip (Snaps GPS to 50-meter cell centroids)
CREATE OR REPLACE FUNCTION fn_mask_to_50m_centroid(raw_geom GEOMETRY(Point, 4326))
RETURNS GEOMETRY(Point, 4326) AS $$
DECLARE
    grid_size_degrees DOUBLE PRECISION := 0.00045;
BEGIN
    RETURN ST_SetSRID(
        ST_MakePoint(
            floor(ST_X(raw_geom) / grid_size_degrees) * grid_size_degrees + (grid_size_degrees / 2.0),
            floor(ST_Y(raw_geom) / grid_size_degrees) * grid_size_degrees + (grid_size_degrees / 2.0)
        ),
        4326
    );
END;
$$ LANGUAGE plpgsql IMMUTABLE STRICT;

CREATE OR REPLACE FUNCTION trg_enforce_popia_geo_mask()
RETURNS TRIGGER AS $$
BEGIN
    NEW.pickup_point := fn_mask_to_50m_centroid(NEW.pickup_point);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_mask_handoff_geo ON transaction_handoff_logs;
CREATE TRIGGER trg_mask_handoff_geo
BEFORE INSERT OR UPDATE ON transaction_handoff_logs
FOR EACH ROW EXECUTE FUNCTION trg_enforce_popia_geo_mask();
