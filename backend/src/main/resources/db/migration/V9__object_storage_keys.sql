-- =====================================================================================
-- V9 : passage du stockage disque local au stockage objet (S3 / MinIO).
--
-- document_metadata.chemin_fichier contenait un chemin absolu du systeme de fichiers
-- ('C:\Users\...\forsalaw-documents\<uuid>.pdf' ou '/home/app/forsalaw-documents/<uuid>.pdf'
-- selon la machine ayant recu l'upload). Il contient desormais une CLE D'OBJET S3,
-- de la forme 'documents/<uuid>.<ext>'.
--
-- ATTENTION - les fichiers locaux existants NE SONT PAS migres.
-- Une migration SQL ne peut pas deplacer des octets du disque vers le bucket. ForsaLaw n'a
-- jamais tourne en production : les fichiers deja presents sur disque sont consideres comme
-- jetables et deviennent inaccessibles apres cette migration. Les lignes en base restent
-- coherentes (elles pointent vers la cle attendue), seul le contenu binaire est absent.
--
-- La cle est derivee de nom_stockage, qui contient deja exactement '<uuid>.<ext>' : on evite
-- ainsi de parser des chemins dont le separateur varie selon l'OS d'origine.
--
-- NE PAS TOUCHER aux deux colonnes suivantes, qui ressemblent a des chemins mais n'en sont pas :
--   * reclamation_attachment.chemin_fichier -> contient un ID de document ('DOC-000123')
--   * messenger_attachment.storage_key      -> contient un ID de document ('DOC-000123')
-- Ces deux tables referencent le coffre-fort par ID et heritent donc du stockage objet
-- sans modification ; les reecrire casserait la resolution des pieces jointes.
-- =====================================================================================

UPDATE document_metadata
   SET chemin_fichier = 'documents/' || nom_stockage
 WHERE chemin_fichier NOT LIKE 'documents/%';
