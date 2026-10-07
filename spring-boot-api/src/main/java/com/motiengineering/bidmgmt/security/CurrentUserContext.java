package com.motiengineering.bidmgmt.security;

import com.motiengineering.bidmgmt.domain.AppUser;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-request holder for the resolved {@link AppUser} and the divisions they
 * belong to, populated by {@link CurrentUserFilter} once JWT authentication
 * has run. There is no multi-tenant concept here (this is a single-company
 * internal tool) - this plays the same per-request-context role that
 * TenantContext plays in the reference clinic-management-saas project, just
 * scoped to "who is this" rather than "which tenant".
 */
public final class CurrentUserContext {

    private static final ThreadLocal<CurrentUserContext> HOLDER = new ThreadLocal<>();

    private final AppUser user;
    private final Set<UUID> divisionIds;
    private final Map<UUID, Boolean> managerByDivisionId;

    private CurrentUserContext(AppUser user, Set<UUID> divisionIds, Map<UUID, Boolean> managerByDivisionId) {
        this.user = user;
        this.divisionIds = divisionIds;
        this.managerByDivisionId = managerByDivisionId;
    }

    public static void set(AppUser user, Set<UUID> divisionIds, Map<UUID, Boolean> managerByDivisionId) {
        HOLDER.set(new CurrentUserContext(user, divisionIds, managerByDivisionId));
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static AppUser getUser() {
        CurrentUserContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.user;
    }

    /** @throws IllegalStateException if no authenticated app user is resolved on this request. */
    public static AppUser requireUser() {
        AppUser user = getUser();
        if (user == null) {
            throw new IllegalStateException("No current user resolved for this request");
        }
        return user;
    }

    public static Set<UUID> getDivisionIds() {
        CurrentUserContext ctx = HOLDER.get();
        return ctx == null ? Set.of() : ctx.divisionIds;
    }

    public static boolean isManagerOf(UUID divisionId) {
        CurrentUserContext ctx = HOLDER.get();
        return ctx != null && Boolean.TRUE.equals(ctx.managerByDivisionId.get(divisionId));
    }
}
