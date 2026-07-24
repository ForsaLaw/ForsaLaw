-- ============================================================================
-- V6__messenger_receipt_backfill.sql
-- ----------------------------------------------------------------------------
-- Remplace l'ancien MessengerMessageReceiptBackfill (ApplicationRunner supprime).
-- Aligne une fois les accuses read/delivered par message sur les timestamps de
-- lecture deja presents au niveau conversation (avant l'introduction des accuses
-- par message). Idempotent (guards IS NULL) et no-op sur une base neuve (tables vides).
-- ============================================================================

UPDATE messenger_message m
   SET read_at_by_client = c.client_last_read_at
  FROM messenger_conversation c
 WHERE m.conversation_id = c.id
   AND m.sender_role = 'AVOCAT'
   AND c.client_last_read_at IS NOT NULL
   AND m.read_at_by_client IS NULL
   AND m.created_at <= c.client_last_read_at;

UPDATE messenger_message m
   SET read_at_by_avocat = c.avocat_last_read_at
  FROM messenger_conversation c
 WHERE m.conversation_id = c.id
   AND m.sender_role = 'CLIENT'
   AND c.avocat_last_read_at IS NOT NULL
   AND m.read_at_by_avocat IS NULL
   AND m.created_at <= c.avocat_last_read_at;

UPDATE messenger_message
   SET delivered_at_to_client = read_at_by_client
 WHERE sender_role = 'AVOCAT'
   AND read_at_by_client IS NOT NULL
   AND delivered_at_to_client IS NULL;

UPDATE messenger_message
   SET delivered_at_to_avocat = read_at_by_avocat
 WHERE sender_role = 'CLIENT'
   AND read_at_by_avocat IS NOT NULL
   AND delivered_at_to_avocat IS NULL;
