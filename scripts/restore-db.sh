#!/usr/bin/env bash
#
# Restauration PostgreSQL de ForsaLaw.
#
#   ./scripts/restore-db.sh --list                      liste les sauvegardes disponibles
#   ./scripts/restore-db.sh --latest                    restaure la plus recente
#   ./scripts/restore-db.sh forsalaw-20260725-101500.dump
#
# OPERATION DESTRUCTIVE : --clean supprime les objets existants avant de les recreer.
# Le script exige donc une confirmation tapee explicitement.
#
# APRES UNE RESTAURATION, VERIFIER L'INTEGRITE DU JOURNAL D'AUDIT :
#   GET /api/admin/audit-logs/integrity
# Une sauvegarde restauree est precisement le vecteur qu'un chainage cryptographique sert
# a detecter : rien ne garantit, sans ce controle, que le dump n'a pas ete modifie.

set -euo pipefail

RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FICHIER_ENV="${FICHIER_ENV:-$RACINE/.env}"

if [[ -f "$FICHIER_ENV" ]]; then
    # shellcheck disable=SC1090
    set -a; source "$FICHIER_ENV"; set +a
fi

DB_NAME="${DB_NAME:-forsalaw}"
DB_USERNAME="${DB_USERNAME:?DB_USERNAME est requis}"
SERVICE_POSTGRES="${SERVICE_POSTGRES:-postgres}"
COMPOSE_FILE="${COMPOSE_FILE:-$RACINE/docker-compose.prod.yml}"

REPERTOIRE_LOCAL="${BACKUP_DIR:-$RACINE/backups}"
BUCKET_BACKUP="${BACKUP_S3_BUCKET:-forsalaw-backups}"
S3_ENDPOINT_BACKUP="${BACKUP_S3_ENDPOINT:-${S3_ENDPOINT:-http://localhost:9000}}"
S3_ACCESS_KEY="${BACKUP_S3_ACCESS_KEY:-${S3_ACCESS_KEY:-}}"
S3_SECRET_KEY="${BACKUP_S3_SECRET_KEY:-${S3_SECRET_KEY:-}}"

journal() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }

configurer_mc() {
    command -v mc >/dev/null 2>&1 || return 1
    [[ -n "$S3_ACCESS_KEY" && -n "$S3_SECRET_KEY" ]] || return 1
    mc alias set forsalaw-backup "$S3_ENDPOINT_BACKUP" "$S3_ACCESS_KEY" "$S3_SECRET_KEY" >/dev/null
}

lister() {
    journal "Sauvegardes locales ($REPERTOIRE_LOCAL) :"
    ls -1t "$REPERTOIRE_LOCAL"/forsalaw-*.dump 2>/dev/null | sed 's|.*/|  |' || echo "  (aucune)"
    if configurer_mc; then
        journal "Sauvegardes distantes ($BUCKET_BACKUP) :"
        mc find "forsalaw-backup/$BUCKET_BACKUP/postgres" --name 'forsalaw-*.dump' 2>/dev/null \
            | sed 's|.*/|  |' || echo "  (aucune)"
    fi
}

# ─── Analyse des arguments ──────────────────────────────────────────────────
ARGUMENT="${1:-}"
case "$ARGUMENT" in
    --list|-l|"")
        lister
        [[ -z "$ARGUMENT" ]] && { echo; echo "Usage : $0 [--list|--latest|<nom-du-dump>]"; }
        exit 0
        ;;
    --latest)
        NOM_DUMP="$(ls -1t "$REPERTOIRE_LOCAL"/forsalaw-*.dump 2>/dev/null | head -1 | xargs -r basename)"
        [[ -n "$NOM_DUMP" ]] || { journal "ECHEC : aucune sauvegarde locale trouvee."; exit 1; }
        ;;
    *)
        NOM_DUMP="$(basename "$ARGUMENT")"
        ;;
esac

CHEMIN_DUMP="$REPERTOIRE_LOCAL/$NOM_DUMP"

# Absente en local : tenter de la recuperer depuis le stockage objet.
if [[ ! -f "$CHEMIN_DUMP" ]]; then
    journal "Sauvegarde absente en local, recherche distante..."
    if configurer_mc; then
        mkdir -p "$REPERTOIRE_LOCAL"
        DISTANT="$(mc find "forsalaw-backup/$BUCKET_BACKUP/postgres" --name "$NOM_DUMP" 2>/dev/null | head -1)"
        [[ -n "$DISTANT" ]] || { journal "ECHEC : '$NOM_DUMP' introuvable localement et a distance."; exit 1; }
        mc cp "$DISTANT" "$CHEMIN_DUMP"
    else
        journal "ECHEC : '$CHEMIN_DUMP' introuvable et le client 'mc' n'est pas configure."
        exit 1
    fi
fi

# ─── Confirmation ───────────────────────────────────────────────────────────
cat <<AVERTISSEMENT

  ATTENTION — OPERATION DESTRUCTIVE
  ---------------------------------
  Base cible   : $DB_NAME (service '$SERVICE_POSTGRES')
  Sauvegarde   : $NOM_DUMP
  Effet        : les objets existants sont SUPPRIMES puis recrees a partir du dump.
                 Toute donnee posterieure a cette sauvegarde sera PERDUE.

AVERTISSEMENT

read -r -p "Taper exactement 'RESTAURER' pour continuer : " CONFIRMATION
if [[ "$CONFIRMATION" != "RESTAURER" ]]; then
    journal "Annule : aucune modification effectuee."
    exit 1
fi

# ─── Restauration ───────────────────────────────────────────────────────────
journal "Restauration en cours..."
# --clean --if-exists : supprime proprement les objets existants sans echouer sur ceux qui
# n'existent pas. --exit-on-error pour ne pas laisser une base a moitie restauree.
docker compose -f "$COMPOSE_FILE" exec -T \
    -e PGPASSWORD="${DB_PASSWORD:?DB_PASSWORD est requis}" \
    "$SERVICE_POSTGRES" \
    pg_restore -U "$DB_USERNAME" -d "$DB_NAME" --clean --if-exists --exit-on-error --single-transaction \
    < "$CHEMIN_DUMP"

journal "Restauration terminee."
journal ""
journal "ETAPE SUIVANTE OBLIGATOIRE : verifier le journal d'audit"
journal "  GET /api/admin/audit-logs/integrity  (compte administrateur)"
journal "Une chaine rompue signifierait que le dump restaure a ete altere."
