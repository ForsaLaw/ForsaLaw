-- ============================================================================
-- V3__rdv_timestamptz.sql — RendezVous : timestamps naifs -> timestamptz (instants).
-- ----------------------------------------------------------------------------
-- Phase 7 (time safety) : l'entite RendezVous passe de LocalDateTime a OffsetDateTime.
-- Les colonnes concernees sont de VRAIS instants (debut/fin du RDV + audit create/update).
--
-- Les valeurs existantes sont naives : elles ont ete ecrites en heure locale tunisienne.
-- `<colonne> AT TIME ZONE 'Africa/Tunis'` interprete chaque valeur comme heure locale
-- Africa/Tunis et la convertit vers l'instant (timestamptz) correspondant. Sans cette
-- clause, PostgreSQL supposerait UTC et decalerait tous les RDV existants (+1h).
--
-- NB : la configuration d'agenda (AvocatPlageRecurrente.heure_*, AvocatAgendaException.date_*)
-- reste en LocalTime/LocalDate (horaire mural recurrent), donc N'EST PAS modifiee ici.
-- ============================================================================

ALTER TABLE rendez_vous
    ALTER COLUMN date_heure_debut TYPE timestamptz USING date_heure_debut AT TIME ZONE 'Africa/Tunis',
    ALTER COLUMN date_heure_fin   TYPE timestamptz USING date_heure_fin   AT TIME ZONE 'Africa/Tunis',
    ALTER COLUMN date_creation    TYPE timestamptz USING date_creation    AT TIME ZONE 'Africa/Tunis',
    ALTER COLUMN date_mise_a_jour TYPE timestamptz USING date_mise_a_jour AT TIME ZONE 'Africa/Tunis';
