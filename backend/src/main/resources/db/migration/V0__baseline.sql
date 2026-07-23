-- ============================================================================
-- V0__baseline.sql — Schéma de base ForsaLaw (généré depuis les entités JPA)
-- ----------------------------------------------------------------------------
-- Représente le schéma canonique produit par Hibernate pour toutes les @Entity.
-- Généré hors-ligne via Hibernate SchemaExport (dialecte PostgreSQL, sans base).
--
-- Comportement Flyway (voir application.properties) :
--   * baseline-on-migrate=true, baseline-version=0
--   * Base EXISTANTE (non vide) : marquée « baseline v0 » ; ce script N'EST PAS
--     rejoué (le schéma existe déjà). Seules V1 (RAG) et V2 (fix audit) s'appliquent.
--   * Base NEUVE (vide) : ce script crée tout le schéma, puis V1 et V2 s'appliquent.
--
-- Ne pas éditer à la main pour suivre le modèle : ajouter plutôt une migration Vn.
-- ============================================================================


    create table affaires (
        id varchar(20) not null,
        date_cloture timestamp(6),
        date_mise_a_jour timestamp(6) not null,
        date_ouverture timestamp(6) not null,
        date_prochaine_audience timestamp(6),
        description TEXT,
        notes_internes TEXT,
        statut varchar(20) not null check (statut in ('INSTRUCTION','AUDIENCE','DELIBERE','JUGEMENT','APPEL','CLOS')),
        titre varchar(250) not null,
        type varchar(20) not null check (type in ('CIVIL','PENAL','FAMILLE','TRAVAIL','COMMERCIAL','IMMOBILIER','ADMINISTRATIF')),
        avocat_id varchar(20),
        client_id varchar(20) not null,
        reclamation_id varchar(20),
        rendez_vous_id varchar(20) unique,
        primary key (id)
    );

    create table audit_log (
        id varchar(20) not null,
        action varchar(100) not null,
        created_at timestamp(6) not null,
        details TEXT,
        endpoint varchar(255),
        http_status integer,
        ip_address varchar(64),
        method varchar(20),
        module_name varchar(100) not null,
        resource_id varchar(100),
        user_agent varchar(500),
        actor_user_id varchar(20),
        primary key (id)
    );

    create table avocat_agenda_config (
        avocat_id varchar(20) not null,
        agenda_actif boolean not null,
        buffer_minutes integer not null,
        date_creation timestamp(6) not null,
        date_mise_a_jour timestamp(6) not null,
        duree_creneau_minutes integer not null,
        zone_id varchar(64) not null,
        primary key (avocat_id)
    );

    create table avocat_agenda_exception (
        id varchar(24) not null,
        date_debut date not null,
        date_fin date not null,
        libelle varchar(500),
        avocat_id varchar(20) not null,
        primary key (id)
    );

    create table avocat_plage_recurrente (
        id varchar(24) not null,
        day_of_week integer not null,
        heure_debut time(6) not null,
        heure_fin time(6) not null,
        avocat_id varchar(20) not null,
        primary key (id)
    );

    create table avocats (
        id varchar(20) not null,
        actif boolean not null,
        anneesexperience integer not null,
        barreau varchar(100),
        cin varchar(50),
        datecreation timestamp(6) not null,
        datemiseajour timestamp(6) not null,
        description TEXT,
        notemoyenne float(53) not null,
        numero_carte_professionnelle varchar(100),
        specialite varchar(255) not null check (specialite in ('travail','penal','civil','famille','fiscal','commercial','DROIT_CONSTITUTIONNEL','DROIT_ADMINISTRATIF','DROIT_FISCAL_ETAT','FINANCES_PUBLIQUES','DROIT_ELECTORAL','DROIT_MARCHES_PUBLICS','DROIT_DE_LA_FAMILLE','DROIT_DES_SOCIETES','DROIT_DES_CONTRATS','DROIT_DES_BIENS','DROIT_IMMOBILIER','DROIT_DES_SUCCESSIONS','DROIT_DES_ASSURANCES','DROIT_DE_LA_CONSOMMATION','DROIT_PENAL_GENERAL','DROIT_PENAL_SPECIAL','DROIT_PENAL_DES_AFFAIRES','DROIT_PENAL_INTERNATIONAL','DROIT_PENAL_MILITAIRE','DROIT_DU_TRAVAIL','DROIT_DE_LA_SECURITE_SOCIALE','DROIT_SYNDICAL','DROIT_DE_LA_PROTECTION_SOCIALE','DROIT_BANCAIRE','DROIT_FINANCIER','DROIT_BOURSIER','DROIT_DE_LA_CONCURRENCE','DROIT_DES_FAILLITES','DROIT_INTERNATIONAL_PUBLIC','DROIT_INTERNATIONAL_PRIVE','DROIT_INTERNATIONAL_ECONOMIQUE','DROIT_HUMANITAIRE_INTERNATIONAL','DROIT_DAUTEUR','DROIT_DES_BREVETS','DROIT_DES_MARQUES','DROIT_DES_DESSINS_ET_MODELES','DROIT_DE_LA_POLLUTION','DROIT_CLIMATIQUE','DROIT_DES_RESSOURCES_NATURELLES','DROIT_MARITIME','DROIT_AERIEN','DROIT_DU_TRANSPORT','DROIT_SPATIAL','RESPONSABILITE_MEDICALE','DROIT_HOSPITALIER','BIOETHIQUE','VISAS','NATURALISATION','DROIT_DES_REFUGIES','DISCIPLINE_MILITAIRE','TRIBUNAUX_MILITAIRES','CYBERSECURITE','DONNEES_PERSONNELLES_RGPD','INTELLIGENCE_ARTIFICIELLE','DROIT_DE_INTERNET','DROIT_PETROLE_GAZ','DROIT_ELECTRICITE','ENERGIE_RENOUVELABLE','EXPLOITATION_AGRICOLE','SECURITE_ALIMENTAIRE')),
        totaldossiers integer not null,
        verification_comment varchar(1000),
        verification_status varchar(20) check (verification_status in ('PENDING','APPROVED','REJECTED')),
        verifie boolean not null,
        ville varchar(100) not null,
        user_id varchar(20) not null unique,
        primary key (id),
        unique (numero_carte_professionnelle),
        unique (cin)
    );

    create table document_access_log (
        id varchar(20) not null,
        action varchar(50) not null check (action in ('UPLOAD','TELECHARGEMENT','CONSULTATION','VERIFICATION_INTEGRITE','SIGNATURE','SUPPRESSION')),
        adresse_ip varchar(64),
        date_action timestamp(6) not null,
        details TEXT,
        integrite_valide boolean,
        acteur_id varchar(20),
        document_id varchar(20) not null,
        primary key (id)
    );

    create table document_metadata (
        id varchar(20) not null,
        chemin_fichier varchar(1000) not null,
        contexte_id varchar(20),
        contexte_type varchar(50) check (contexte_type in ('RECLAMATION','MESSENGER','DOSSIER','GENERAL','PROFIL_UTILISATEUR')),
        date_creation timestamp(6) not null,
        date_signature timestamp(6),
        est_signe boolean default false not null,
        hash_apres_signature varchar(64),
        hash_sha256 varchar(64) not null,
        nom_original varchar(500) not null,
        nom_stockage varchar(255) not null,
        signataire_email varchar(255),
        supprime boolean not null,
        taille_fichier bigint,
        type_contenu varchar(100),
        deposeur_id varchar(20) not null,
        primary key (id)
    );

    create table forum_message (
        id varchar(20) not null,
        content TEXT not null,
        created_at timestamp(6) not null,
        updated_at timestamp(6) not null,
        author_user_id varchar(20) not null,
        topic_id varchar(20) not null,
        primary key (id)
    );

    create table forum_message_reaction (
        id varchar(20) not null,
        reaction_type varchar(32) not null check (reaction_type in ('LIKE','DISLIKE','LOVE','LAUGH','INSIGHTFUL')),
        message_id varchar(20) not null,
        user_id varchar(20) not null,
        primary key (id),
        constraint uk_forum_reaction_message_user unique (message_id, user_id)
    );

    create table forum_topic (
        id varchar(20) not null,
        content TEXT not null,
        created_at timestamp(6) not null,
        title varchar(200) not null,
        updated_at timestamp(6) not null,
        author_user_id varchar(20) not null,
        primary key (id)
    );

    create table id_sequences (
        entity_type varchar(10) not null,
        year integer not null,
        next_val bigint not null,
        primary key (entity_type, year)
    );

    create table messenger_attachment (
        id varchar(20) not null,
        content_type varchar(120) not null,
        created_at timestamp(6) not null,
        original_filename varchar(500) not null,
        scan_status varchar(20) not null check (scan_status in ('SKIPPED','PENDING','CLEAN','INFECTED','ERROR')),
        size_bytes bigint not null,
        storage_key varchar(500) not null,
        message_id varchar(20) not null,
        primary key (id)
    );

    create table messenger_conversation (
        id varchar(20) not null,
        avocat_last_read_at timestamp(6),
        client_last_read_at timestamp(6),
        closed_at timestamp(6),
        created_at timestamp(6) not null,
        last_message_at timestamp(6),
        last_message_preview varchar(500),
        status varchar(20) not null check (status in ('OPEN','CLOSED')),
        updated_at timestamp(6) not null,
        avocat_id varchar(20) not null,
        client_user_id varchar(20) not null,
        primary key (id),
        constraint uk_messenger_conv_client_avocat unique (client_user_id, avocat_id)
    );

    create table messenger_message (
        id varchar(20) not null,
        content TEXT not null,
        created_at timestamp(6) not null,
        delivered_at_to_avocat timestamp(6),
        delivered_at_to_client timestamp(6),
        read_at_by_avocat timestamp(6),
        read_at_by_client timestamp(6),
        sender_role varchar(20) not null check (sender_role in ('CLIENT','AVOCAT')),
        conversation_id varchar(20) not null,
        sender_user_id varchar(20) not null,
        primary key (id)
    );

    create table reclamation (
        id varchar(20) not null,
        a_nouvelle_notification boolean not null,
        categorie varchar(50) not null check (categorie in ('TECHNIQUE','JURIDIQUE','FACTURATION','AUTRE')),
        date_creation timestamp(6) not null,
        date_modification timestamp(6) not null,
        description TEXT not null,
        gravite varchar(50) not null check (gravite in ('BASSE','MOYENNE','HAUTE','CRITIQUE')),
        statut varchar(50) not null check (statut in ('OUVERTE','EN_COURS','TRAITEE','REJETEE')),
        titre varchar(255) not null,
        createur_id varchar(20) not null,
        utilisateur_cible_id varchar(20),
        primary key (id)
    );

    create table reclamation_attachment (
        id bigserial not null,
        chemin_fichier varchar(500) not null,
        date_envoi timestamp(6) not null,
        nom_fichier varchar(255) not null,
        taille_fichier bigint,
        type_contenu varchar(100),
        reclamation_id varchar(20) not null,
        primary key (id)
    );

    create table reclamation_message (
        id bigserial not null,
        contenu TEXT not null,
        date_envoi timestamp(6) not null,
        expediteur_id varchar(20) not null,
        reclamation_id varchar(20) not null,
        primary key (id)
    );

    create table rendez_vous (
        id_rendez_vous varchar(20) not null,
        commentaire_avocat varchar(1000),
        cree_par varchar(20) not null check (cree_par in ('CLIENT','AVOCAT')),
        date_creation timestamp(6) not null,
        date_heure_debut timestamp(6),
        date_heure_fin timestamp(6),
        date_mise_a_jour timestamp(6) not null,
        meeting_url varchar(1000),
        motif_consultation varchar(1000),
        raison_annulation varchar(1000),
        rappel_h1_envoye boolean not null,
        rappel_j1_envoye boolean not null,
        statut_rendez_vous varchar(20) not null check (statut_rendez_vous in ('EN_ATTENTE','PROPOSE','CONFIRME','ANNULE')),
        type_rendez_vous varchar(20) not null check (type_rendez_vous in ('EN_LIGNE','CABINET','TELEPHONE')),
        avocat_id varchar(20) not null,
        client_user_id varchar(20) not null,
        primary key (id_rendez_vous)
    );

    create table user_notification_preferences (
        user_id varchar(20) not null,
        email_rdv_annulation boolean not null,
        email_rdv_creneau_propose boolean not null,
        email_rdv_demande_recue boolean not null,
        email_rdv_rappel_h1 boolean not null,
        email_rdv_rappel_j1 boolean not null,
        primary key (user_id)
    );

    create table users (
        id varchar(20) not null,
        actif boolean not null,
        blocked_by_failed_attempts boolean default false not null,
        datecreation timestamp(6) not null,
        datemiseajour timestamp(6) not null,
        email varchar(255) not null unique,
        failed_login_attempts integer default 0 not null,
        motdepasse varchar(255) not null,
        nom varchar(255) not null,
        password_reset_expires_at timestamp(6),
        password_reset_token varchar(120),
        prenom varchar(255) not null,
        profile_photo_document_id varchar(20),
        role_user varchar(255) not null check (role_user in ('client','avocat','admin')),
        telephone varchar(20),
        primary key (id)
    );

    create index IDXit5ri5o3fjj6qnpopx69bdfbq 
       on affaires (client_id);

    create index IDXkti7jgxm5yueg7ap7bpr20795 
       on affaires (avocat_id);

    create index IDXce5xsqavittjmu8y45wtb6bgv 
       on affaires (statut);

    create index idx_audit_log_actor 
       on audit_log (actor_user_id);

    create index idx_audit_log_created_at 
       on audit_log (created_at);

    create index idx_audit_log_module 
       on audit_log (module_name);

    create index idx_exc_avocat 
       on avocat_agenda_exception (avocat_id);

    create index idx_plage_avocat 
       on avocat_plage_recurrente (avocat_id);

    create index idx_access_log_document 
       on document_access_log (document_id);

    create index idx_access_log_user 
       on document_access_log (acteur_id);

    create index idx_access_log_date 
       on document_access_log (date_action);

    create index idx_doc_deposeur 
       on document_metadata (deposeur_id);

    create index idx_doc_contexte 
       on document_metadata (contexte_type, contexte_id);

    create index idx_doc_date_creation 
       on document_metadata (date_creation);

    alter table if exists affaires 
       add constraint FKnykbbyhbqlu6660tgdp5trojb 
       foreign key (avocat_id) 
       references avocats;

    alter table if exists affaires 
       add constraint FKalsq01nx0so15rkm19nflc771 
       foreign key (client_id) 
       references users;

    alter table if exists affaires 
       add constraint FK4cwfr1d0xfvbcqiw2jv4ejnwx 
       foreign key (reclamation_id) 
       references reclamation;

    alter table if exists affaires 
       add constraint FKqw21w2e9f050j4hy3m1u8inxy 
       foreign key (rendez_vous_id) 
       references rendez_vous;

    alter table if exists audit_log 
       add constraint FKhwhphg953f29t00yv7cvub49m 
       foreign key (actor_user_id) 
       references users;

    alter table if exists avocat_agenda_config 
       add constraint FK6e4ky4v7mx10gexdbvb99j0pl 
       foreign key (avocat_id) 
       references avocats;

    alter table if exists avocat_agenda_exception 
       add constraint FKly77lbver2ac277gx375i4gfv 
       foreign key (avocat_id) 
       references avocats;

    alter table if exists avocat_plage_recurrente 
       add constraint FKsd9w87mrr4bwlyrwen6l7jlb9 
       foreign key (avocat_id) 
       references avocats;

    alter table if exists avocats 
       add constraint FK21sjbuct3ww32atq3glg4ho43 
       foreign key (user_id) 
       references users;

    alter table if exists document_access_log 
       add constraint FKtd7h0qmrc6s58phwcyri7mgc7 
       foreign key (acteur_id) 
       references users;

    alter table if exists document_access_log 
       add constraint FKkii9y9im6ibc4jv0j36d87wyh 
       foreign key (document_id) 
       references document_metadata;

    alter table if exists document_metadata 
       add constraint FKkg3o8pls7evxf50o7lcbe7jxh 
       foreign key (deposeur_id) 
       references users;

    alter table if exists forum_message 
       add constraint FKc73agjxqycmaajx5ir50sek3t 
       foreign key (author_user_id) 
       references users;

    alter table if exists forum_message 
       add constraint FKadj2wmiq2tnq6ad3j7etyppt3 
       foreign key (topic_id) 
       references forum_topic;

    alter table if exists forum_message_reaction 
       add constraint FKrj68wwjrmqmk9t4enpg0wubkm 
       foreign key (message_id) 
       references forum_message 
       on delete cascade;

    alter table if exists forum_message_reaction 
       add constraint FK1a3cap7ixlxp7l0sl351xb8ce 
       foreign key (user_id) 
       references users;

    alter table if exists forum_topic 
       add constraint FKqfe6hajhpe0i73eyc62e1xp5u 
       foreign key (author_user_id) 
       references users;

    alter table if exists messenger_attachment 
       add constraint FKr63ikeuyq7qt9k62w6374rrdq 
       foreign key (message_id) 
       references messenger_message;

    alter table if exists messenger_conversation 
       add constraint FKe5vp2cn18devfa45k515kaoyv 
       foreign key (avocat_id) 
       references avocats;

    alter table if exists messenger_conversation 
       add constraint FKcbn8umc9su0mx9x8c5qtsgo1c 
       foreign key (client_user_id) 
       references users;

    alter table if exists messenger_message 
       add constraint FKlyjsdxx5jmh4gym2mh1m38g2d 
       foreign key (conversation_id) 
       references messenger_conversation;

    alter table if exists messenger_message 
       add constraint FKikmlna273y55t6p62kh58awex 
       foreign key (sender_user_id) 
       references users;

    alter table if exists reclamation 
       add constraint FKq3h112nwjyeled57kmgq0f1ow 
       foreign key (createur_id) 
       references users;

    alter table if exists reclamation 
       add constraint FKpfefv4cmwxb86cgei18mk6b8v 
       foreign key (utilisateur_cible_id) 
       references users;

    alter table if exists reclamation_attachment 
       add constraint FKko7o244t153450ekxx724i7hn 
       foreign key (reclamation_id) 
       references reclamation;

    alter table if exists reclamation_message 
       add constraint FK2i7hfw2yojwcr2dp75w6idld9 
       foreign key (expediteur_id) 
       references users;

    alter table if exists reclamation_message 
       add constraint FKj0c1gyp0skpmknxs747gkg3b0 
       foreign key (reclamation_id) 
       references reclamation;

    alter table if exists rendez_vous 
       add constraint FKhnjhub0a6haprfogby9563mc 
       foreign key (avocat_id) 
       references avocats;

    alter table if exists rendez_vous 
       add constraint FK2dph7ak78l5fy3n4ydikov3ew 
       foreign key (client_user_id) 
       references users;

    alter table if exists user_notification_preferences 
       add constraint FKjqii7bt0v7fyg56obr7nio4ax 
       foreign key (user_id) 
       references users;
