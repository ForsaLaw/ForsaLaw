package com.forsalaw.avocatManagement.model;

import com.forsalaw.avocatManagement.entity.DomaineJuridique;
import com.forsalaw.avocatManagement.entity.SpecialiteJuridique;
import com.forsalaw.avocatManagement.entity.AvocatVerificationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AvocatDTO {

    private String id;
    private String userId;
    private String userNom;
    private String userPrenom;
    private String userEmail;
    private DomaineJuridique domaine;
    private String domaineLibelle;
    private SpecialiteJuridique specialite;
    private String specialiteLibelle;
    private int anneesExperience;
    private String ville;
    private String description;
    private String numeroCarteProfessionnelle;
    private String numeroOnat;
    private String cin;
    private String barreau;
    private double noteMoyenne;
    private int totalDossiers;
    private boolean verifie;
    private AvocatVerificationStatus verificationStatus;
    private String verificationComment;
    private boolean actif;
    private LocalDateTime dateCreation;
    private LocalDateTime dateMiseAJour;

    /** URL publique (sans auth) pour afficher la photo si l'avocat en a une ; sinon null. */
    private String profilePhotoPublicUrl;
}
