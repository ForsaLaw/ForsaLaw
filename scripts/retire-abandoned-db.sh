#!/usr/bin/env bash
#
# Met hors d'atteinte la base PostgreSQL abandonnee "forsalaw".
#
#   ./scripts/retire-abandoned-db.sh              # simulation, n'ecrit rien
#   ./scripts/retire-abandoned-db.sh --apply      # renomme reellement
#
# CONTEXTE — deux bases coexistent sur l'instance de developpement :
#
#   forsalaw_rag  base ACTIVE : 15 migrations (V0->V14), corpus juridique complet.
#   forsalaw      base ABANDONNEE : bloquee sur la migration V1, corpus vide, tables RAG
#                 absentes (legal_instrument notamment).
#
# S'y connecter par erreur ne provoque pas une erreur franche : l'application demarre, puis
# echoue plus loin ("relation legal_instrument does not exist") ou, pire, sert des reponses
# vides comme si le corpus n'existait pas. Le nom "forsalaw" etant le defaut naturel, la
# confusion est facile — elle s'est deja produite.
#
# Choix du RENOMMAGE plutot que du DROP : la base abandonnee contient encore des comptes
# utilisateurs historiques. Les detruire est irreversible et sans urgence ; la rendre
# inatteignable par son nom evident suffit a supprimer le risque. Le DROP reste possible plus
# tard, une fois certain que rien n'en depend.

set -euo pipefail

CONTENEUR="${POSTGRES_CONTAINER:-forsalaw-postgres}"
UTILISATEUR="${DB_USERNAME:-forsalaw}"
MOT_DE_PASSE="${DB_PASSWORD:-forsalaw}"
BASE_ABANDONNEE="${ABANDONED_DB:-forsalaw}"
BASE_ACTIVE="${ACTIVE_DB:-forsalaw_rag}"
NOUVEAU_NOM="${BASE_ABANDONNEE}_ABANDONNEE"

APPLIQUER="false"
[[ "${1:-}" == "--apply" ]] && APPLIQUER="true"

journal() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }

psql_sur() {
    docker exec -e PGPASSWORD="$MOT_DE_PASSE" "$CONTENEUR" \
        psql -U "$UTILISATEUR" -d "$1" -tAc "$2"
}

existe() {
    local n
    n=$(psql_sur postgres "SELECT count(*) FROM pg_database WHERE datname = '$1';")
    [[ "$n" == "1" ]]
}

# ─── Verifications prealables ───────────────────────────────────────────────
if ! existe "$BASE_ABANDONNEE"; then
    journal "La base '$BASE_ABANDONNEE' n'existe pas : rien a faire."
    exit 0
fi

# Ne jamais renommer la base abandonnee si l'active manque : ce serait le signe que les roles
# sont inverses (ou que l'on parle a la mauvaise instance), et l'operation aggraverait le cas.
if ! existe "$BASE_ACTIVE"; then
    journal "ECHEC : la base active '$BASE_ACTIVE' est introuvable sur ce serveur."
    journal "        Refus d'agir : verifier que '$CONTENEUR' est bien l'instance attendue."
    exit 1
fi

# Confirmation par les donnees, pas par le nom : c'est le corpus qui distingue les deux bases.
CHUNKS_ACTIVE=$(psql_sur "$BASE_ACTIVE" "SELECT count(*) FROM legal_document_chunk;" 2>/dev/null || echo "0")
journal "Base active   '$BASE_ACTIVE'      : ${CHUNKS_ACTIVE} extraits de corpus."

VERSION_ABANDONNEE=$(psql_sur "$BASE_ABANDONNEE" \
    "SELECT COALESCE(max(version), '(aucune)') FROM flyway_schema_history;" 2>/dev/null || echo "(inconnue)")
journal "Base abandonnee '$BASE_ABANDONNEE' : derniere migration ${VERSION_ABANDONNEE}."

if [[ "$CHUNKS_ACTIVE" == "0" ]]; then
    journal "ECHEC : la base active ne contient aucun extrait de corpus."
    journal "        C'est incoherent avec le role attendu — refus d'agir."
    exit 1
fi

if existe "$NOUVEAU_NOM"; then
    journal "'$NOUVEAU_NOM' existe deja : l'operation a vraisemblablement deja ete faite."
    exit 0
fi

# ─── Application ────────────────────────────────────────────────────────────
if [[ "$APPLIQUER" != "true" ]]; then
    journal "SIMULATION — aucune modification effectuee."
    journal "Action prevue : ALTER DATABASE $BASE_ABANDONNEE RENAME TO $NOUVEAU_NOM;"
    journal "Relancer avec --apply pour executer."
    exit 0
fi

# Un renommage echoue tant qu'une session est ouverte sur la base : on les ferme d'abord.
journal "Fermeture des connexions ouvertes sur '$BASE_ABANDONNEE'..."
psql_sur postgres "SELECT pg_terminate_backend(pid) FROM pg_stat_activity
                   WHERE datname = '$BASE_ABANDONNEE' AND pid <> pg_backend_pid();" >/dev/null

journal "Renommage '$BASE_ABANDONNEE' -> '$NOUVEAU_NOM'..."
psql_sur postgres "ALTER DATABASE $BASE_ABANDONNEE RENAME TO $NOUVEAU_NOM;" >/dev/null

journal "Termine. Toute connexion a '$BASE_ABANDONNEE' echouera desormais franchement,"
journal "au lieu de reussir sur une base vide."
