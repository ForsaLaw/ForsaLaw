package com.forsalaw.ragManagement.chat.model;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Corps de POST /api/ai/consent. */
@Getter
@Setter
@NoArgsConstructor
public class AiConsentRequest {

    /** Version du texte de consentement acceptee (AI_CONSENT_VERSION cote front). */
    @Min(value = 1, message = "La version du consentement doit etre un entier positif.")
    private int version;
}
