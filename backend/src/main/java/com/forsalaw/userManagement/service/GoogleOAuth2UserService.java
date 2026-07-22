package com.forsalaw.userManagement.service;

import com.forsalaw.userManagement.entity.RoleUser;
import com.forsalaw.userManagement.entity.User;
import com.forsalaw.userManagement.entity.IdSequence;
import com.forsalaw.userManagement.repository.IdSequenceRepository;
import com.forsalaw.userManagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class GoogleOAuth2UserService {

    private final UserRepository userRepository;
    private final IdSequenceRepository idSequenceRepository;

    @Transactional
    public User upsertGoogleUser(OAuth2User oauthUser) {
        String email = (String) oauthUser.getAttributes().get("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google n'a pas fourni d'email.");
        }
        String normalizedEmail = email.trim().toLowerCase();

        return userRepository.findByEmail(normalizedEmail).map(existing -> {
            // Reactive le compte ; le role existant (provisionne en base) n'est jamais ecrase au login.
            existing.setActif(true);
            return userRepository.save(existing);
        }).orElseGet(() -> {
            User user = new User();
            user.setId(generateNextId("USR"));
            user.setEmail(normalizedEmail);
            user.setNom(resolveNom(oauthUser));
            user.setPrenom(resolvePrenom(oauthUser));
            // Compte OAuth2 : mot de passe local non utilise
            user.setMotDePasse("{noop}GOOGLE_OAUTH2_ACCOUNT");
            // Premiere connexion Google => CLIENT. Les elevations de role se font en base / via admin.
            user.setRoleUser(RoleUser.client);
            user.setActif(true);
            return userRepository.save(user);
        });
    }

    private String generateNextId(String prefix) {
        int year = LocalDate.now().getYear();
        var seq = idSequenceRepository.findByEntityTypeAndYearForUpdate(prefix, year);
        long nextVal;
        if (seq.isEmpty()) {
            IdSequence newSeq = new IdSequence();
            newSeq.setEntityType(prefix);
            newSeq.setYear(year);
            newSeq.setNextVal(2);
            idSequenceRepository.save(newSeq);
            nextVal = 1;
        } else {
            IdSequence s = seq.get();
            nextVal = s.getNextVal();
            s.setNextVal(nextVal + 1);
            idSequenceRepository.save(s);
        }
        return year + "-" + prefix + "-" + String.format("%05d", nextVal);
    }

    private String resolveNom(OAuth2User oauthUser) {
        String familyName = (String) oauthUser.getAttributes().get("family_name");
        if (familyName != null && !familyName.isBlank()) {
            return familyName;
        }
        String fullName = (String) oauthUser.getAttributes().get("name");
        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.trim().split("\\s+");
            return parts.length > 1 ? parts[parts.length - 1] : fullName;
        }
        return "GoogleUser";
    }

    private String resolvePrenom(OAuth2User oauthUser) {
        String givenName = (String) oauthUser.getAttributes().get("given_name");
        if (givenName != null && !givenName.isBlank()) {
            return givenName;
        }
        String fullName = (String) oauthUser.getAttributes().get("name");
        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.trim().split("\\s+");
            return parts[0];
        }
        return "User";
    }
}
