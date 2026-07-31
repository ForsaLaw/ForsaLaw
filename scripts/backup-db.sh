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
# forsalaw_rag est la base ACTIVE. Le defaut precedent ("forsalaw") designait la base
# abandonnee, restee bloquee sur la migration V1 : une sauvegarde lancee sans DB_NAME
# explicite produisait donc un dump parfaitement valide... d'une base vide. Voir
# scripts/retire-abandoned-db.sh, qui met cette base hors d'atteinte.
DB_NAME="${DB_NAME:-forsalaw_rag}"
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

# Sans copie hors de l'hote, il n'y a pas de reprise apres sinistre : la perte de la machine
# emporte la base ET ses sauvegardes. Le script REFUSE donc de se terminer en succes dans ce
# cas — un cron qui rapporte "OK" sur une sauvegarde non deportee est pire que pas de cron du
# tout, puisqu'il fait croire que le sujet est traite. Mettre BACKUP_ALLOW_LOCAL_ONLY=true
# pour l'accepter sciemment (poste de developpement, restauration ponctuelle).
AUTORISER_LOCAL_SEUL="${BACKUP_ALLOW_LOCAL_ONLY:-false}"

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

# Le controle de taille ne dit pas si l'archive est LISIBLE. pg_restore --list la parcourt
# reellement : un dump tronque ou corrompu echoue ici, alors qu'il passait le seuil d'octets.
journal "Verification de l'integrite de l'archive..."
if ! docker compose -f "$COMPOSE_FILE" exec -T "$SERVICE_POSTGRES" \
        pg_restore --list < "$CHEMIN_DUMP" > /dev/null 2>&1; then
    journal "ECHEC : l'archive est illisible par pg_restore, elle ne serait pas restaurable."
    rm -f "$CHEMIN_DUMP"
    exit 1
fi
journal "Archive lisible par pg_restore."

# ─── 2. Envoi vers le stockage objet ────────────────────────────────────────
# Sauvegarder vers le MEME stockage que l'application ne protege de rien : l'incident qui
# emporte l'hote emporte les deux. On le signale explicitement plutot que de le laisser
# passer inapercu derriere une valeur par defaut.
if [[ -z "${BACKUP_S3_ENDPOINT:-}" ]]; then
    journal "AVERTISSEMENT : BACKUP_S3_ENDPOINT n'est pas defini — repli sur le stockage"
    journal "               applicatif ($S3_ENDPOINT_BACKUP). Si celui-ci partage l'hote de"
    journal "               PostgreSQL, la perte de l'hote emporte la base ET les sauvegardes."
fi

if ! command -v mc >/dev/null 2>&1; then
    journal "Le client 'mc' est absent : aucun envoi hors de cet hote n'est possible."
    if [[ "$AUTORISER_LOCAL_SEUL" != "true" ]]; then
        journal "ECHEC : sauvegarde purement locale refusee (BACKUP_ALLOW_LOCAL_ONLY=true pour l'accepter)."
        exit 1
    fi
    journal "AVERTISSEMENT : accepte via BACKUP_ALLOW_LOCAL_ONLY — ce n'est PAS une reprise apres sinistre."
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
