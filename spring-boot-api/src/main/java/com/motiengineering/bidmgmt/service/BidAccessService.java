package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.OpportunityDivisionRepository;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Who can see and edit what, per the brief: Director and Division Managers
 * see/edit everything in their scope (all divisions for the Director, their
 * own division(s) for a manager); Account Officers can edit only the bids
 * they're personally assigned to as a lot officer; Scouts can create
 * tenders and view lists, but never edit.
 */
@Service
@RequiredArgsConstructor
public class BidAccessService {

    private final LotAccountOfficerRepository lotAccountOfficerRepository;
    private final OpportunityDivisionRepository opportunityDivisionRepository;

    /** Everyone with a login can view every bid - division scoping only restricts editing, not visibility. */
    public boolean canView(Bid bid) {
        return CurrentUserContext.getUser() != null;
    }

    public boolean canCreate() {
        return CurrentUserContext.getUser() != null;
    }

    public boolean canEdit(Bid bid) {
        AppUser user = CurrentUserContext.getUser();
        if (user == null) {
            return false;
        }
        if (user.getRole() == Role.DIRECTOR) {
            return true;
        }
        if (user.getRole() == Role.SCOUT) {
            return false;
        }
        if (user.getRole() == Role.DIVISION_MANAGER) {
            return bid.getLots().stream()
                    .anyMatch(lot -> lot.getDivision() != null && CurrentUserContext.getDivisionIds().contains(lot.getDivision().getId()));
        }
        // ACCOUNT_OFFICER: only bids where they're assigned as an officer on at least one lot.
        return bid.getLots().stream().anyMatch(lot -> isAssignedOfficer(lot, user));
    }

    public boolean canEditLot(BidLot lot) {
        return canEdit(lot.getBid());
    }

    private boolean isAssignedOfficer(BidLot lot, AppUser user) {
        return lotAccountOfficerRepository.findById_LotId(lot.getId()).stream()
                .anyMatch((LotAccountOfficer loa) -> loa.getId().getUserId().equals(user.getId()));
    }

    /** Director/Division Manager (in-scope) edit any opportunity; an Account Officer edits only one they own; Scouts are view-only, same as bids. */
    public boolean canEditOpportunity(Opportunity opportunity) {
        AppUser user = CurrentUserContext.getUser();
        if (user == null) {
            return false;
        }
        if (user.getRole() == Role.DIRECTOR) {
            return true;
        }
        if (user.getRole() == Role.SCOUT) {
            return false;
        }
        if (user.getRole() == Role.DIVISION_MANAGER) {
            return opportunityDivisionRepository.findById_OpportunityId(opportunity.getId()).stream()
                    .anyMatch(od -> CurrentUserContext.getDivisionIds().contains(od.getId().getDivisionId()));
        }
        return opportunity.getOwner() != null && opportunity.getOwner().getId().equals(user.getId());
    }

    /**
     * Deal registrations have no division of their own (they're keyed on
     * customer + OEM, not a sales division) - Director, Division Managers
     * and Account Officers can all edit any of them; only Scouts can't.
     */
    public boolean canEditDealRegistration(DealRegistration registration) {
        AppUser user = CurrentUserContext.getUser();
        return user != null && user.getRole() != Role.SCOUT;
    }
}
