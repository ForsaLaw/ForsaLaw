-- ============================================================================
-- V13__article_title_sans_limite.sql — article_title VARCHAR(500) -> TEXT.
-- ----------------------------------------------------------------------------
-- Constat a l'ingestion reelle de legislation-securite (DCAF) : 2 documents sur 5528 ont
-- echoue avec « value too long for type character varying(500) ». Le titre d'un arrete DCAF
-- est une phrase complete ("Arrete des ministres de ... du 19 avril 2018, portant approbation
-- du reglement applicable a ... en application des articles 107 et 115 de la loi organique
-- n 2015-26 du 7 aout 2015 ..."), pas un intitule court comme pour un article de code
-- (jurisite). Mesure : 561 et 547 caracteres, au-dela de la limite posee en V1 pour des
-- titres d'articles courts.
--
-- TEXT plutot qu'un VARCHAR(N) plus genereux : aucune borne naturelle ne se degage du
-- corpus (un titre de decret peut legitimement s'etendre sur plusieurs visas), et TEXT n'a
-- aucun cout de stockage ou de performance supplementaire par rapport a VARCHAR en
-- PostgreSQL — seule la contrainte de longueur disparait.
-- ============================================================================

ALTER TABLE legal_document_chunk ALTER COLUMN article_title TYPE TEXT;

COMMENT ON COLUMN legal_document_chunk.article_title IS
    'Intitule de l''article (jurisite, court) ou du document (DCAF, phrase complete). '
    'Sans limite de longueur depuis V13 : voir le corpus reel pour la justification.';
