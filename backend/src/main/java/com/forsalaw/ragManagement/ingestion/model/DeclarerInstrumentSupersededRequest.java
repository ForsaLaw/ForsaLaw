package com.forsalaw.ragManagement.ingestion.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/** Declaration qu'un instrument entier (code, constitution...) en remplace un autre. */
@Data
public class DeclarerInstrumentSupersededRequest {

    private String title;

    /** Date d'entree en vigueur de l'instrument remplace, si connue. */
    private LocalDate inForceFrom;

    /** Date a laquelle l'instrument remplace cesse d'etre le droit applicable. */
    @NotNull(message = "inForceUntil est obligatoire")
    private LocalDate inForceUntil;

    /** code_name de l'instrument qui prend le relais (ex. "Constitution_2022"). */
    @NotBlank(message = "supersededByCodeName est obligatoire")
    private String supersededByCodeName;

    /** Justification (texte libre) : d'ou vient cette information, sur quel fondement. */
    @NotBlank(message = "note est obligatoire : justifier la declaration")
    private String note;
}
