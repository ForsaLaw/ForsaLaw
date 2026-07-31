package com.forsalaw.ragManagement.chat.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {

    @NotBlank(message = "question est obligatoire")
    private String question;

    /** 1 = legislation, 2 = jurisprudence. Le niveau 3 (coffre prive) n'est pas accessible ici. */
    @Min(value = 1, message = "tier doit valoir 1 ou 2 pour cet assistant")
    @Max(value = 2, message = "tier doit valoir 1 ou 2 pour cet assistant")
    private int tier = 1;
}
