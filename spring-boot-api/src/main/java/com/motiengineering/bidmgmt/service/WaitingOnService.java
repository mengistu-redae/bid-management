package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.WaitingOnState;
import com.motiengineering.bidmgmt.domain.WaitingOnStateId;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.WaitingOnParty;
import com.motiengineering.bidmgmt.repository.WaitingOnStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaitingOnService {

    private final WaitingOnStateRepository waitingOnStateRepository;
    private final StageHistoryService stageHistoryService;

    public Optional<WaitingOnState> get(EntityType entityType, UUID entityId) {
        return waitingOnStateRepository.findById(new WaitingOnStateId(entityType, entityId));
    }

    @Transactional
    public WaitingOnState set(EntityType entityType, UUID entityId, WaitingOnParty partyType, AppUser person, String note, AppUser actingUser) {
        WaitingOnStateId id = new WaitingOnStateId(entityType, entityId);
        WaitingOnState state = waitingOnStateRepository.findById(id).orElseGet(() -> new WaitingOnState(entityType, entityId));
        WaitingOnParty oldParty = state.getPartyType();

        state.setPartyType(partyType);
        state.setPerson(person);
        state.setNote(note);
        state.setUpdatedBy(actingUser);
        state = waitingOnStateRepository.save(state);

        if (oldParty != partyType) {
            stageHistoryService.record(entityType, entityId, "waitingOn", oldParty == null ? null : oldParty.name(), partyType.name(), note, actingUser);
        }
        return state;
    }
}
