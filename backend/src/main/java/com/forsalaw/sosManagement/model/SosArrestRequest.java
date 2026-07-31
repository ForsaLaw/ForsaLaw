package com.forsalaw.sosManagement.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Saisie d'un signalement SOS.
 *
 * <p>Volontairement reduite au strict necessaire : ce formulaire est rempli dans l'urgence, par
 * un proche bouleverse. Chaque champ ajoute est une chance de plus d'abandonner en cours de
 * route — les precisions facultatives tiennent toutes dans {@code details}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class SosArrestRequest {

    @NotBlank(message = "Le nom de la personne arretee est obligatoire.")
    @Size(max = 255)
    private String nomDetenu;

    @NotBlank(message = "Le lieu de l'arrestation est obligatoire.")
    @Size(max = 255)
    private String lieuArrestation;

    @NotNull(message = "La date et l'heure de l'arrestation sont obligatoires.")
    private LocalDateTime dateHeureArrestation;

    /**
     * Format tunisien tolerant : indicatif optionnel, espaces admis. Delibérément permissif —
     * rejeter un numero valide mal formate ferait perdre l'urgence pour une question de saisie.
     */
    @NotBlank(message = "Un numero de contact joignable est obligatoire.")
    @Pattern(
            regexp = "^\\+?[0-9 ]{8,20}$",
            message = "Le numero de contact doit comporter 8 a 20 chiffres."
    )
    private String contactUrgence;

    @Size(max = 4000)
    private String details;
}
