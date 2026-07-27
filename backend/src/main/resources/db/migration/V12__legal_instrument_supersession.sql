-- ============================================================================
-- V12__legal_instrument_supersession.sql — Suivi de l'abrogation au niveau INSTRUMENT.
-- ----------------------------------------------------------------------------
-- Constat sur le corpus reel ingere (jurisite, 1836 fichiers) : la Constitution de 2014
-- (281 chunks, tous status = ACTIVE) est INTEGRALEMENT remplacee par celle de 2022, mais rien
-- dans le texte de la Constitution de 2022 ne dit « la Constitution de 2014 est abrogee » —
-- une refonte totale ne s'exprime jamais comme une abrogation d'article. rag_supersede_proposal
-- (V11) ne peut donc structurellement pas detecter ce cas : il cherche des tournures
-- d'abrogation ARTICLE PAR ARTICLE dans le texte ingere. Consequence mesuree : une recherche
-- semantique sur une question constitutionnelle faisait remonter l'article de 2014 en premier
-- resultat, sans aucune indication qu'il etait perime.
--
-- Ce n'est pas un probleme de detection a affiner : c'est une information qu'aucun texte
-- n'exprime, et qui doit donc etre saisie, pas devinee. D'ou une table separee, alimentee par
-- deux voies :
--   - MANUEL   : un humain declare qu'un instrument entier remplace un autre (ci-dessous,
--                Constitution_2014 -> Constitution_2022).
--   - DCAF     : legislation-securite fournit un champ `statut` publisher-maintained
--                (en vigueur / abroge / n'est plus en vigueur) pour chacun de ses 5528
--                documents. C'est une source de verite existante : autant la reprendre que
--                la re-deviner par regex sur un texte qu'on n'a pas ecrit.
--
-- Volontairement PAS de detection automatique par regex a ce niveau (contrairement a
-- rag_supersede_proposal) : un remplacement total ne peut pas se lire dans le texte du
-- remplacant, la seule source fiable est une declaration humaine ou un champ deja publie
-- par l'editeur du texte.
--
-- Choix architectural : DATE-AWARE, PAS UN FILTRE.
-- Un filtre qui masquerait tout instrument non EN_VIGUEUR casserait la question qu'un
-- avocat pose le plus souvent : « ce contrat etait-il licite en 2018 ? ». Cette question
-- doit pouvoir remonter un texte ABROGE aujourd'hui s'il etait en vigueur a la date visee.
-- legal_instrument porte donc une FENETRE de validite (in_force_from / in_force_until),
-- pas un simple drapeau EN_VIGUEUR/ABROGE : le statut affiche est calcule par rapport a une
-- date de reference (« as of »), jamais applique comme un filtre silencieux.
-- ============================================================================

-- ─── 1. Instruments juridiques (code entier, ou document autonome type decret) ─────────────

CREATE TABLE legal_instrument (
    id                       BIGSERIAL PRIMARY KEY,
    -- Cle naturelle : identique a legal_document_chunk.code_name pour un CODE (ex.
    -- "Constitution_2014"), ou a l'identifiant par-fichier pour un document autonome type
    -- DCAF (chaque decret/arrete est SON PROPRE instrument, il n'y a pas de regroupement).
    code_name                VARCHAR(255) NOT NULL UNIQUE,
    title                    TEXT,
    -- NULL = date d'entree en vigueur inconnue (frequent sur les textes anciens). Une date
    -- absente est exploitable (aucune contrainte appliquee) ; une date inventee ne l'est pas.
    in_force_from            DATE,
    -- NULL = toujours en vigueur pour autant qu'on le sache.
    in_force_until           DATE,
    -- Lien FAIBLE (pas de FK) vers un autre legal_instrument.code_name : au moment ou un
    -- remplacement est declare, l'instrument remplacant peut ne pas encore avoir sa propre
    -- ligne (corpus ingere dans un ordre quelconque). Meme convention que tenant_id en V11 :
    -- l'integrite est de responsabilite applicative, pas contrainte en base.
    superseded_by_code_name  VARCHAR(255),
    status                   VARCHAR(20) NOT NULL DEFAULT 'INCONNU'
                             CHECK (status IN ('EN_VIGUEUR', 'ABROGE', 'NON_EN_VIGUEUR', 'INCONNU')),
    -- Provenance de l'information : distingue une declaration humaine d'un champ publisher.
    source                   VARCHAR(20) NOT NULL CHECK (source IN ('MANUEL', 'DCAF')),
    -- Format AAAA-XXX-NNNNN (IdSequenceService) non applicable : le sujet du jeton JWT est
    -- l'email (JwtService.subject), potentiellement long. NULL pour les lignes source=DCAF
    -- (aucun humain n'a saisi cette ligne, seul le champ `statut` du corpus l'a alimentee).
    declared_by              VARCHAR(255),
    declared_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_note              TEXT,
    UNIQUE (code_name)
);

CREATE INDEX idx_legal_instrument_status ON legal_instrument (status);
CREATE INDEX idx_legal_instrument_superseded_by ON legal_instrument (superseded_by_code_name)
    WHERE superseded_by_code_name IS NOT NULL;

COMMENT ON TABLE legal_instrument IS
    'Fenetre de validite d''un instrument entier (code ou document autonome). Complement de '
    'legal_document_chunk.status, qui ne sait exprimer qu''une abrogation ARTICLE PAR ARTICLE. '
    'Consulte par date de reference (as-of), jamais comme un filtre : voir le service de recherche.';
COMMENT ON COLUMN legal_instrument.code_name IS
    'Identique a legal_document_chunk.code_name pour un code ; identifiant par-fichier pour '
    'un document autonome (voir CorpusImportRunner, repli sur le champ `id` du front-matter).';
COMMENT ON COLUMN legal_instrument.status IS
    'INCONNU par defaut : l''absence de ligne ou un statut INCONNU ne doit JAMAIS masquer un '
    'resultat de recherche, seulement omettre l''indication de statut.';

-- ─── 2. legal_document_chunk : date a laquelle une SUPERSESSION ARTICLE devient effective ──
-- V11 ne portait qu'un statut binaire (ACTIVE/SUPERSEDED) sans date : impossible de repondre
-- « cet article etait-il en vigueur en 2018 ? » pour un article individuellement abroge,
-- alors que c'est exactement ce que legal_instrument permet au niveau du document entier.
-- NULL tant qu'aucune proposition n'a ete confirmee (les 4 detectees a ce jour sont des faux
-- positifs -- voir rag_supersede_proposal.matched_text -- et ne DOIVENT PAS etre confirmees).

ALTER TABLE legal_document_chunk ADD COLUMN IF NOT EXISTS superseded_at DATE;

COMMENT ON COLUMN legal_document_chunk.superseded_at IS
    'Date a partir de laquelle ce chunk est SUPERSEDED (renseignee uniquement lors de la '
    'confirmation administrateur d''une proposition, jamais par la detection automatique). '
    'NULL tant que status = ACTIVE.';

-- ─── 3. rag_supersede_proposal.reviewed_by : dimensionnement corrige avant premier usage ───
-- VARCHAR(20) (V11) supposait le format AAAA-XXX-NNNNN d'IdSequenceService. Aucun endpoint de
-- confirmation n'existait encore pour l'utiliser (voir docs : "Not built by design"). Il se
-- trouve que le sujet du jeton JWT est l'email (JwtService.subject) : une adresse plus longue
-- aurait ete tronquee silencieusement des le premier appel a l'endpoint construit ici.

ALTER TABLE rag_supersede_proposal ALTER COLUMN reviewed_by TYPE VARCHAR(255);

-- ─── 4. Declarations manuelles connues ──────────────────────────────────────────────────────
-- Dates a faire VERIFIER par un avocat avant toute utilisation en production (meme discipline
-- que backend/src/test/resources/rag-eval/eval_set.json : "verified": false tant que non
-- confirme). Poser une date fausse dans une colonne qui pilote desormais le classement
-- temporel des resultats serait pire que ne rien poser.

INSERT INTO legal_instrument
    (code_name, title, in_force_from, in_force_until, superseded_by_code_name,
     status, source, declared_by, source_note)
VALUES
    ('Constitution_2014',
     'Constitution de la Republique tunisienne du 27 janvier 2014',
     '2014-01-27', '2022-08-16', 'Constitution_2022',
     'ABROGE', 'MANUEL', NULL,
     'Abrogee et remplacee par la Constitution de 2022 (referendum du 25 juillet 2022, '
     'promulguee le 16 aout 2022). DATES A VERIFIER PAR UN AVOCAT avant usage en production.'),
    ('Constitution_2022',
     'Constitution de la Republique tunisienne du 25 juillet 2022',
     '2022-08-16', NULL, NULL,
     'EN_VIGUEUR', 'MANUEL', NULL,
     'Adoptee par referendum le 25 juillet 2022, promulguee le 16 aout 2022. '
     'DATES A VERIFIER PAR UN AVOCAT avant usage en production.')
ON CONFLICT (code_name) DO NOTHING;

-- Le troisieme code_name observe dans le corpus jurisite ingere ("constitution", 77 chunks,
-- fichiers constitution/const*.md, PAS de front-matter `code:` donc distinct de
-- "Constitution_2014"/"Constitution_2022") N'EST PAS renseigne ici : sa nature (version
-- consolidee ? texte plus ancien ?) n'a pas ete etablie. Mieux vaut l'omettre que lui assigner
-- un statut suppose.
