-- ============================================================================
-- V16__avocat_numero_onat.sql
-- ----------------------------------------------------------------------------
-- Numero d'inscription a l'Ordre National des Avocats de Tunisie (ONAT).
--
-- Porte par "avocats" et NON par "users" : seuls les avocats en ont un, et la table users
-- accueille aussi clients et administrateurs. Une colonne sur users serait vide pour la quasi
-- totalite des lignes.
--
-- NULLABLE, sans valeur par defaut : les profils avocats deja enregistres n'ont jamais eu a
-- fournir ce numero. Le rendre NOT NULL echouerait sur ces lignes (defaut deja constate sur
-- rendez_vous.date_mise_a_jour). Le caractere obligatoire est donc porte par la VALIDATION DE
-- SAISIE des nouvelles demandes, pas par une contrainte de schema qui invaliderait
-- retroactivement le parc existant.
--
-- A NOTER : "avocats" comporte deja numero_carte_professionnelle, egalement obligatoire a la
-- saisie. En Tunisie, la carte professionnelle est delivree par l'ONAT — les deux numeros
-- peuvent donc designer la meme realite. Ils sont conserves distincts ici, faute de certitude,
-- mais ce point merite d'etre tranche par le metier avant que les deux ne divergent.
-- ============================================================================

ALTER TABLE avocats
    ADD COLUMN IF NOT EXISTS numero_onat VARCHAR(100);

-- Unicite : deux avocats ne peuvent pas partager un numero d'inscription. Index PARTIEL, pour
-- que les lignes anterieures restees a NULL n'entrent pas en collision entre elles.
CREATE UNIQUE INDEX IF NOT EXISTS ux_avocats_numero_onat
    ON avocats (LOWER(numero_onat))
    WHERE numero_onat IS NOT NULL;
