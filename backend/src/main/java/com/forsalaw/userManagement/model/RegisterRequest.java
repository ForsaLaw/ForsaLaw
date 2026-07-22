package com.forsalaw.userManagement.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Le nom est requis")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String nom;

    @NotBlank(message = "Le prénom est requis")
    @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
    private String prenom;

    @NotBlank(message = "L'email est requis")
    @Email(message = "Email invalide")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Le mot de passe est requis")
    @Size(max = 100, message = "Le mot de passe ne doit pas dépasser 100 caractères")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message = "Le mot de passe doit contenir au moins 8 caractères, dont au moins une lettre et un chiffre"
    )
    private String motDePasse;
}
