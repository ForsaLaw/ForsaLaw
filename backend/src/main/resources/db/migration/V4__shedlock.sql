-- ============================================================================
-- V4__shedlock.sql — Table de verrou distribue ShedLock.
-- ----------------------------------------------------------------------------
-- Empeche l'execution concurrente d'une meme tache planifiee sur plusieurs
-- instances backend (ex. rappels e-mail RDV en double). ShedLock ecrit une ligne
-- par tache verrouillee ; seule l'instance qui detient le verrou execute la tache.
--
-- Colonnes en timestamptz : le LockProvider est configure en usingDbTime()
-- (heure du serveur PostgreSQL), evitant tout ecart d'horloge entre instances.
-- ============================================================================

CREATE TABLE shedlock (
    name       VARCHAR(64)              NOT NULL,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by  VARCHAR(255)             NOT NULL,
    PRIMARY KEY (name)
);
