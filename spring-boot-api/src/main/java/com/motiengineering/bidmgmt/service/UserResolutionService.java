package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.util.TextSimilarity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Resolves a bare first name from the spreadsheets (e.g. "Selam", "Nardos")
 * to the matching seeded {@link AppUser}, falling back to creating a
 * reference-only user (no login yet) for a name nobody seeded - keeps the
 * importer working even for a name the Director didn't mention up front.
 */
@Service
@RequiredArgsConstructor
public class UserResolutionService {

    private static final double FUZZY_MATCH_THRESHOLD = 0.84;

    private final AppUserRepository appUserRepository;

    @Transactional
    public AppUser resolveOrCreate(String rawName, Role roleIfNew) {
        String trimmed = rawName.trim();
        List<AppUser> exact = appUserRepository.findByFullNameIgnoreCase(trimmed);
        if (!exact.isEmpty()) {
            return exact.get(0);
        }

        AppUser best = null;
        double bestScore = 0;
        for (AppUser candidate : appUserRepository.findAll()) {
            double score = TextSimilarity.similarity(trimmed.toLowerCase(Locale.ROOT), candidate.getFullName().toLowerCase(Locale.ROOT));
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null && bestScore >= FUZZY_MATCH_THRESHOLD) {
            return best;
        }

        AppUser created = new AppUser();
        created.setFullName(trimmed);
        created.setRole(roleIfNew);
        created.setActive(true);
        return appUserRepository.save(created);
    }
}
