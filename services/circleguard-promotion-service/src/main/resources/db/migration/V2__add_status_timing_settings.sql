-- Idempotent so a fresh schema (no V1 ever created system_settings) and an
-- already-bootstrapped schema (table already there from JPA ddl-auto) both
-- end up in the same shape. Columns mirror SystemSettings.java.

CREATE TABLE IF NOT EXISTS system_settings (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    unconfirmed_fencing_enabled BOOLEAN NOT NULL DEFAULT false,
    auto_threshold_seconds BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS mandatory_fence_days  INTEGER NOT NULL DEFAULT 14,
    ADD COLUMN IF NOT EXISTS encounter_window_days INTEGER NOT NULL DEFAULT 14;

INSERT INTO system_settings
    (unconfirmed_fencing_enabled, auto_threshold_seconds, mandatory_fence_days, encounter_window_days)
SELECT false, 0, 14, 14
WHERE NOT EXISTS (SELECT 1 FROM system_settings);
