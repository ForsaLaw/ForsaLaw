package com.forsalaw.avocatManagement.model;

import com.forsalaw.avocatManagement.entity.DomaineJuridique;
import com.forsalaw.avocatManagement.entity.SpecialiteJuridique;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Création d'un profil avocat : l'avocat choisit obligatoirement son domaine (droit) et sa spécialité (sous-domaine).")
public class CreateAvocatRequest {

    @NotNull(message = "Le domaine du droit est requis (ex. Droit public, Droit privé).")
    @Schema(description = "Domaine du droit (obligatoire). Choisir d'abord le domaine, puis la spécialité dans la liste des sous-domaines.", requiredMode = Schema.RequiredMode.REQUIRED)
    private DomaineJuridique domaine;

    @NotNull(message = "La spécialité (sous-domaine) est requise.")
    @Schema(description = "Spécialité juridique / sous-domaine (obligatoire). Doit appartenir au domaine choisi. GET /api/avocats/domaines pour la hiérarchie.", requiredMode = Schema.RequiredMode.REQUIRED)
    private SpecialiteJuridique specialite;

    @Min(0)
    private int anneesExperience = 0;

    @NotBlank(message = "La ville est requise")
    @Size(max = 100)
    private String ville;

    @Size(max = 2000)
    private String description;

    @NotBlank(message = "Le numero de carte professionnelle est requis")
    @Size(max = 100)
    private String numeroCarteProfessionnelle;

    /**
     * Numero d'inscription a l'ONAT. Obligatoire pour toute NOUVELLE demande : c'est la piece
     * qui permet de confronter la declaration au tableau de l'Ordre. La colonne reste nullable
     * en base pour les profils anterieurs (voir V16).
     */
    @NotBlank(message = "Le numero d'inscription a l'ONAT est requis")
    @Size(max = 100)
    private String numeroOnat;

    @NotBlank(message = "Le CIN est requis")
    @Size(max = 50)
    private String cin;

    @NotBlank(message = "Le barreau est requis")
    @Size(max = 100)
    private String barreau;
}
