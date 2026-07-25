#!/usr/bin/env bash
#
# Sauvegarde PostgreSQL de ForsaLaw : dump compresse, horodate, pousse vers le bucket
# objet, avec rotation a 30 jours.
#
#   ./scripts/backup-db.sh
#
# Variables lues dans le .env a la racine (celui de docker-compose.prod.yml).
#
# AVERTISSEMENT — CECI N'EST PAS ENCORE UN PLAN DE REPRISE COMPLET :
#   si MinIO tourne sur le MEME hote que PostgreSQL (cas de docker-compose.prod.yml),
#   la perte de cet hote emporte la base ET ses sauvegardes. Une reprise apres sinistre
#   digne de ce nom exige une copie HORS de cet hote : bucket S3 distant, replication
#   MinIO, ou synchronisation vers un stockage tiers. Renseigner BACKUP_S3_ENDPOINT vers
#   un stockage externe pour couvrir ce cas.
#
# DONNEES PERSONNELLES : ces dumps contiennent l'integralite des donnees, y compris les
# donnees personnelles. Ils heritent donc des memes obligations que la base elle-meme —
# voir docs/ERASURE_POLICY.md, section "Sauvegardes".

set -euo pipefail

RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FICHIER_ENV="${FICHIER_ENV:-$RACINE/.env}"

if [[ -f "$FICHIER_ENV" ]]; then
    # shellcheck disable=SC1090
    set -a; source "$FICHIER_ENV"; set +a
fi

# ─── Configuration ──────────────────────────────────────────────────────────
DB_NAME="${DB_NAME:-forsalaw}"
DB_USERNAME="${DB_USERNAME:?DB_USERNAME est requis}"
SERVICE_POSTGRES="${SERVICE_POSTGRES:-postgres}"
COMPOSE_FILE="${COMPOSE_FILE:-$RACINE/docker-compose.prod.yml}"

REPERTOIRE_LOCAL="${BACKUP_DIR:-$RACINE/backups}"
RETENTION_JOURS="${BACKUP_RETENTION_DAYS:-30}"

# Bucket DEDIE aux sauvegardes : ne jamais reutiliser celui des documents, sinon une
# suppression ou une compromission du bucket applicatif emporte aussi les sauvegardes.
BUCKET_BACKUP="${BACKUP_S3_BUCKET:-forsalaw-backups}"
S3_ENDPOINT_BACKUP="${BACKUP_S3_ENDPOINT:-${S3_ENDPOINT:-http://localhost:9000}}"
S3_ACCESS_KEY="${BACKUP_S3_ACCESS_KEY:-${S3_ACCESS_KEY:?S3_ACCESS_KEY est requis}}"
S3_SECRET_KEY="${BACKUP_S3_SECRET_KEY:-${S3_SECRET_KEY:?S3_SECRET_KEY est requis}}"

HORODATAGE="$(date -u +%Y%m%d-%H%M%S)"
NOM_DUMP="forsalaw-${HORODATAGE}.dump"
CHEMIN_DUMP="$REPERTOIRE_LOCAL/$NOM_DUMP"
PREFIXE_OBJET="postgres/$(date -u +%Y/%m)"

journal() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }

mkdir -p "$REPERTOIRE_LOCAL"

# ─── 1. Dump ────────────────────────────────────────────────────────────────
# pg_dump est execute DANS le conteneur PostgreSQL : la version du client correspond
# ainsi toujours a celle du serveur (un pg_dump plus ancien que le serveur refuse de
# tourner). Le mot de passe passe par l'environnement du conteneur, jamais en argument
# de ligne de commande (visible dans la table des processus).
journal "Dump de la base '$DB_NAME'..."
docker compose -f "$COMPOSE_FILE" exec -T \
    -e PGPASSWORD="${DB_PASSWORD:?DB_PASSWORD est requis}" \
    "$SERVICE_POSTGRES" \
    pg_dump -U "$DB_USERNAME" -d "$DB_NAME" --format=custom --compress=9 \
    > "$CHEMIN_DUMP"

# Un dump vide ou tronque est pire qu'aucun dump : il donne l'illusion d'une sauvegarde.
TAILLE=$(wc -c < "$CHEMIN_DUMP")
if [[ "$TAILLE" -lt 1024 ]]; then
    journal "ECHEC : le dump ne fait que ${TAILLE} octets, il est vraisemblablement invalide."
    rm -f "$CHEMIN_DUMP"
    exit 1
fi
journal "Dump ecrit : $CHEMIN_DUMP ($(numfmt --to=iec "$TAILLE" 2>/dev/null || echo "${TAILLE} o"))"

# ─── 2. Envoi vers le stockage objet ────────────────────────────────────────
if ! command -v mc >/dev/null 2>&1; then
    journal "AVERTISSEMENT : le client 'mc' est absent, envoi distant ignore."
    journal "               La sauvegarde n'existe QUE localement : ce n'est pas une reprise apres sinistre."
else
    journal "Envoi vers $BUCKET_BACKUP/$PREFIXE_OBJET/ ..."
    mc alias set forsalaw-backup "$S3_ENDPOINT_BACKUP" "$S3_ACCESS_KEY" "$S3_SECRET_KEY" >/dev/null
    mc mb --ignore-existing "forsalaw-backup/$BUCKET_BACKUP" >/dev/null
    mc cp "$CHEMIN_DUMP" "forsalaw-backup/$BUCKET_BACKUP/$PREFIXE_OBJET/$NOM_DUMP"
    journal "Envoi termine."

    # ─── 3. Rotation distante ───────────────────────────────────────────────
    journal "Purge des sauvegardes distantes de plus de ${RETENTION_JOURS} jours..."
    mc rm --recursive --force --older-than "${RETENTION_JOURS}d" \
        "forsalaw-backup/$BUCKET_BACKUP/postgres/" || true
fi

# ─── 4. Rotation locale ─────────────────────────────────────────────────────
journal "Purge des sauvegardes locales de plus de ${RETENTION_JOURS} jours..."
find "$REPERTOIRE_LOCAL" -name 'forsalaw-*.dump' -type f -mtime "+${RETENTION_JOURS}" -delete

journal "Sauvegarde terminee : $NOM_DUMP"
