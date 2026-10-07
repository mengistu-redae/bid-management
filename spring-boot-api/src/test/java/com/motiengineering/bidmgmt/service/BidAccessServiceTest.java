package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.LotAccountOfficerId;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the per-role editing rules from the brief: Director edits
 * everything; a Division Manager edits only bids with a lot in their own
 * division(s); an Account Officer edits only bids they're personally
 * assigned to; a Scout never edits, only creates/views.
 */
class BidAccessServiceTest {

    private final LotAccountOfficerRepository lotAccountOfficerRepository = mock(LotAccountOfficerRepository.class);
    private final BidAccessService service = new BidAccessService(lotAccountOfficerRepository);

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    private AppUser userWithRole(Role role) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }

    private Bid bidWithLotInDivision(UUID divisionId) {
        Division division = new Division();
        division.setId(divisionId);
        BidLot lot = new BidLot();
        lot.setId(UUID.randomUUID());
        lot.setDivision(division);
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        List<BidLot> lots = new ArrayList<>();
        lots.add(lot);
        bid.setLots(lots);
        return bid;
    }

    @Test
    void directorCanEditAnyBid() {
        CurrentUserContext.set(userWithRole(Role.DIRECTOR), Set.of(), Map.of());
        Bid bid = bidWithLotInDivision(UUID.randomUUID());
        assertThat(service.canEdit(bid)).isTrue();
    }

    @Test
    void divisionManagerCanEditOnlyBidsInTheirOwnDivision() {
        UUID myDivision = UUID.randomUUID();
        UUID otherDivision = UUID.randomUUID();
        CurrentUserContext.set(userWithRole(Role.DIVISION_MANAGER), Set.of(myDivision), Map.of(myDivision, true));

        assertThat(service.canEdit(bidWithLotInDivision(myDivision))).isTrue();
        assertThat(service.canEdit(bidWithLotInDivision(otherDivision))).isFalse();
    }

    @Test
    void accountOfficerCanEditOnlyBidsTheyAreAssignedTo() {
        AppUser officer = userWithRole(Role.ACCOUNT_OFFICER);
        CurrentUserContext.set(officer, Set.of(), Map.of());

        Bid assignedBid = bidWithLotInDivision(UUID.randomUUID());
        UUID assignedLotId = assignedBid.getLots().get(0).getId();
        when(lotAccountOfficerRepository.findById_LotId(assignedLotId))
                .thenReturn(List.of(new LotAccountOfficer(new LotAccountOfficerId(assignedLotId, officer.getId()))));

        Bid unassignedBid = bidWithLotInDivision(UUID.randomUUID());
        when(lotAccountOfficerRepository.findById_LotId(unassignedBid.getLots().get(0).getId())).thenReturn(List.of());

        assertThat(service.canEdit(assignedBid)).isTrue();
        assertThat(service.canEdit(unassignedBid)).isFalse();
    }

    @Test
    void scoutCanNeverEditButCanStillViewAndCreate() {
        CurrentUserContext.set(userWithRole(Role.SCOUT), Set.of(), Map.of());
        Bid bid = bidWithLotInDivision(UUID.randomUUID());

        assertThat(service.canEdit(bid)).isFalse();
        assertThat(service.canView(bid)).isTrue();
        assertThat(service.canCreate()).isTrue();
    }

    @Test
    void noLoggedInUserCanNeitherViewNorEdit() {
        Bid bid = bidWithLotInDivision(UUID.randomUUID());
        assertThat(service.canView(bid)).isFalse();
        assertThat(service.canEdit(bid)).isFalse();
        assertThat(service.canCreate()).isFalse();
    }
}
