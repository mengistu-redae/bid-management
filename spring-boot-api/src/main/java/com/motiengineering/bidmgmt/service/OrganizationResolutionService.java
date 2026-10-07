package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.OrganizationAlias;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.repository.OrganizationAliasRepository;
import com.motiengineering.bidmgmt.repository.OrganizationRepository;
import com.motiengineering.bidmgmt.util.TextSimilarity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a free-text organization name (as written by a scout, e.g. "CBE"
 * or "Commercial bank of ethiopia") to one canonical {@link Organization},
 * so "Commercial Bank of Ethiopia" / "CBE" / "Commercial bank of ethiopia"
 * all end up as one customer rather than three. A name below the confidence
 * threshold doesn't get silently merged - the caller sees it as a "new
 * organization" suggestion (via {@link Match#confident()}) and the import
 * preview surfaces it for a human to confirm, rather than this service
 * guessing wrong on a Director-facing customer list.
 */
@Service
@RequiredArgsConstructor
public class OrganizationResolutionService {

    /** Below this normalized edit-distance similarity, treat as a different organization (create new). */
    private static final double FUZZY_MATCH_THRESHOLD = 0.82;

    private final OrganizationRepository organizationRepository;
    private final OrganizationAliasRepository organizationAliasRepository;

    public record Match(Organization organization, boolean exact, boolean confident) {
    }

    /** Read-only lookup for import preview - never creates anything. */
    @Transactional(readOnly = true)
    public Optional<Match> find(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(rawName);

        Optional<OrganizationAlias> aliasHit = organizationAliasRepository.findByAliasNormalized(normalized);
        if (aliasHit.isPresent()) {
            return Optional.of(new Match(aliasHit.get().getOrganization(), true, true));
        }

        List<Organization> all = organizationRepository.findAll();

        // Acronyms (e.g. "CBE" for "Commercial Bank of Ethiopia") can't be
        // bridged by edit-distance at all - a short, all-caps raw name is
        // checked against each candidate's own initials before falling back
        // to fuzzy spelling matching.
        if (isLikelyAcronym(rawName)) {
            for (Organization candidate : all) {
                if (acronymOf(candidate.getName()).equalsIgnoreCase(rawName.trim())) {
                    return Optional.of(new Match(candidate, false, true));
                }
            }
        }

        Organization best = null;
        double bestScore = 0;
        for (Organization candidate : all) {
            double score = TextSimilarity.similarity(normalized, normalize(candidate.getName()));
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        boolean exact = bestScore >= 0.999;
        boolean confident = bestScore >= FUZZY_MATCH_THRESHOLD;
        return Optional.of(new Match(best, exact, confident));
    }

    private static final Set<String> ACRONYM_STOPWORDS = Set.of("of", "the", "and", "for");

    private static boolean isLikelyAcronym(String rawName) {
        String trimmed = rawName.trim();
        return trimmed.length() >= 2 && trimmed.length() <= 6
                && !trimmed.contains(" ")
                && trimmed.equals(trimmed.toUpperCase(Locale.ROOT))
                && trimmed.chars().allMatch(Character::isLetter);
    }

    private static String acronymOf(String fullName) {
        StringBuilder sb = new StringBuilder();
        for (String word : fullName.trim().split("\\s+")) {
            String w = word.replaceAll("[^A-Za-z]", "");
            if (w.isEmpty() || ACRONYM_STOPWORDS.contains(w.toLowerCase(Locale.ROOT))) {
                continue;
            }
            sb.append(Character.toUpperCase(w.charAt(0)));
        }
        return sb.toString();
    }

    /**
     * Resolves for real: reuses a confident match (recording the raw spelling
     * as a new alias if it wasn't already known), otherwise creates a brand
     * new organization under this raw name.
     */
    @Transactional
    public Organization resolveOrCreate(String rawName, Sector sectorIfNew) {
        String trimmedRaw = rawName.replace("\n", " ").trim().replaceAll("\\s+", " ");
        Optional<Match> match = find(trimmedRaw);
        if (match.isPresent() && match.get().confident()) {
            Organization org = match.get().organization();
            String normalized = normalize(trimmedRaw);
            if (organizationAliasRepository.findByAliasNormalized(normalized).isEmpty()) {
                organizationAliasRepository.save(new OrganizationAlias(org, trimmedRaw));
            }
            return org;
        }
        Organization created = new Organization(trimmedRaw, sectorIfNew == null ? Sector.OTHER : sectorIfNew);
        return organizationRepository.save(created);
    }

    private static String normalize(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace("\n", " ").trim();
        n = n.replaceAll("[.,]", "");
        n = n.replaceAll("\\bs\\s*c\\b", ""); // "S.C" / "s c" share-company suffix
        n = n.replaceAll("\\s+", " ").trim();
        return n;
    }
}
