package com.forsalaw.ragManagement.ingestion.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ConfirmerPropositionRequest {

    /**
     * Date a laquelle l'article devient SUPERSEDED. Saisie par l'administrateur : la
     * detection localise une tournure d'abrogation, elle n'etablit pas sa date d'effet.
     */
    @NotNull(message = "supersededAt est obligatoire")
    private LocalDate supersededAt;
}
