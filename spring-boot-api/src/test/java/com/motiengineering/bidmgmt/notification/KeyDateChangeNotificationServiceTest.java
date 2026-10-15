package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AlertState;
import com.motiengineering.bidmgmt.domain.AlertStateId;
import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.AuditLogEntry;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.LotAccountOfficerId;
import com.motiengineering.bidmgmt.domain.Oem;
import com.motiengineering.bidmgmt.domain.Opportunity;
import com.motiengineering.bidmgmt.domain.OpportunityDivision;
import com.motiengineering.bidmgmt.domain.OpportunityDivisionId;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.UserDivisionId;
import com.motiengineering.bidmgmt.domain.enums.AlertKind;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.repository.AlertStateRepository;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.AuditLogRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.OpportunityDivisionRepository;
import com.motiengineering.bidmgmt.repository.OpportunityRepository;
import com.motiengineering.bidmgmt.repository.UserDivisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/** Covers the audit_log scan, the once-ever (not once-a-day) dedup via alert_state, and recipient resolution per entity type. */
class KeyDateChangeNotificationServiceTest {

    private AuditLogRepository auditLogRepository;
    private AlertStateRepository alertStateRepository;
    private BidRepository bidRepository;
    private OpportunityRepository opportunityRepository;
    private OpportunityDivisionRepository opportunityDivisionRepository;
    private DealRegistrationRepository dealRegistrationRepository;
    private LotAccountOfficerRepository lotAccountOfficerRepository;
    private UserDivisionRepository userDivisionRepository;
    private AppUserRepository appUserRepository;
    private NotificationService notificationService;
    private KeyDateChangeNotificationService service;

    private AppUser officer;
    private AppUser manager;
    private AppUser director;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository = mock(AuditLogRepository.class);
        alertStateRepository = mock(AlertStateRepository.class);
        bidRepository = mock(BidRepository.class);
        opportunityRepository = mock(OpportunityRepository.class);
        opportunityDivisionRepository = mock(OpportunityDivisionRepository.class);
        dealRegistrationRepository = mock(DealRegistrationRepository.class);
        lotAccountOfficerRepository = mock(LotAccountOfficerRepository.class);
        userDivisionRepository = mock(UserDivisionRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        notificationService = mock(NotificationService.class);

        service = new KeyDateChangeNotificationService(auditLogRepository, alertStateRepository, bidRepository,
                opportunityRepository, opportunityDivisionRepository, dealRegistrationRepository,
                lotAccountOfficerRepository, userDivisionRepository, appUserRepository, notificationService);
        setField(service, "directorEmail", "director@example.com");
        setField(service, "appBaseUrl", "http://localhost:3002");

        officer = userWithRole(Role.ACCOUNT_OFFICER, "officer@example.com");
        manager = userWithRole(Role.DIVISION_MANAGER, "manager@example.com");
        director = userWithRole(Role.DIRECTOR, "director@example.com");
        when(appUserRepository.findByEmailIgnoreCase("director@example.com")).thenReturn(Optional.of(director));
        when(alertStateRepository.existsById(any())).thenReturn(false);
    }

    private static AppUser userWithRole(Role role, String email) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setFullName(role.name());
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private AuditLogEntry entry(String entityType, UUID entityId, String fieldName) {
        AuditLogEntry entry = new AuditLogEntry(entityType, entityId, fieldName, "2026-10-10T00:00:00Z", "2026-10-20T00:00:00Z", null);
        entry.setId(UUID.randomUUID());
        return entry;
    }

    @Test
    void ignoresAFieldThatIsNotAKeyDate() {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        when(auditLogRepository.findAll()).thenReturn(List.of(entry("BID", bid.getId(), "notes")));

        service.run();

        verify(notificationService, never()).sendIfNotAlreadySent(any(), any(), any(), any(), any());
        verify(alertStateRepository, never()).save(any());
    }

    @Test
    void aBidClosingDateChangeNotifiesTheLotsOfficerAndDivisionManager() {
        Organization org = new Organization("Test Bank", Sector.BANK);
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setTitle("Test Bid");
        bid.setOrganization(org);

        Division division = new Division();
        division.setId(UUID.randomUUID());
        division.setName("Server & Storage");

        BidLot lot = new BidLot();
        lot.setId(UUID.randomUUID());
        lot.setBid(bid);
        lot.setDivision(division);
        List<BidLot> lots = new ArrayList<>();
        lots.add(lot);
        bid.setLots(lots);

        when(bidRepository.findById(bid.getId())).thenReturn(Optional.of(bid));
        when(auditLogRepository.findAll()).thenReturn(List.of(entry("BID", bid.getId(), "closingAt")));

        LotAccountOfficer loa = new LotAccountOfficer(new LotAccountOfficerId(lot.getId(), officer.getId()));
        when(lotAccountOfficerRepository.findById_LotId(lot.getId())).thenReturn(List.of(loa));
        when(appUserRepository.findById(officer.getId())).thenReturn(Optional.of(officer));
        UserDivision userDivision = new UserDivision(new UserDivisionId(manager.getId(), division.getId()), true);
        when(userDivisionRepository.findById_DivisionId(division.getId())).thenReturn(List.of(userDivision));
        when(appUserRepository.findById(manager.getId())).thenReturn(Optional.of(manager));

        service.run();

        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.KEY_DATE_CHANGED), eq(EntityType.BID), eq(bid.getId()), eq(officer), any());
        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.KEY_DATE_CHANGED), eq(EntityType.BID), eq(bid.getId()), eq(manager), any());
        verify(alertStateRepository).save(any(AlertState.class));
    }

    @Test
    void anOpportunityExpectedTenderDateChangeNotifiesItsOwnerAndDivisionManager() {
        Opportunity opportunity = new Opportunity();
        opportunity.setId(UUID.randomUUID());
        opportunity.setTitle("Test Opportunity");
        opportunity.setOwner(officer);

        Division division = new Division();
        division.setId(UUID.randomUUID());

        when(opportunityRepository.findById(opportunity.getId())).thenReturn(Optional.of(opportunity));
        when(auditLogRepository.findAll()).thenReturn(List.of(entry("OPPORTUNITY", opportunity.getId(), "expectedTenderDate")));
        OpportunityDivision od = new OpportunityDivision(new OpportunityDivisionId(opportunity.getId(), division.getId()));
        when(opportunityDivisionRepository.findById_OpportunityId(opportunity.getId())).thenReturn(List.of(od));
        UserDivision userDivision = new UserDivision(new UserDivisionId(manager.getId(), division.getId()), true);
        when(userDivisionRepository.findById_DivisionId(division.getId())).thenReturn(List.of(userDivision));
        when(appUserRepository.findById(manager.getId())).thenReturn(Optional.of(manager));

        service.run();

        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.KEY_DATE_CHANGED), eq(EntityType.OPPORTUNITY), eq(opportunity.getId()), eq(officer), any());
        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.KEY_DATE_CHANGED), eq(EntityType.OPPORTUNITY), eq(opportunity.getId()), eq(manager), any());
    }

    @Test
    void aDealRegistrationExpiryDateChangeNotifiesCreatedByAndTheDirector() {
        DealRegistration reg = new DealRegistration();
        reg.setId(UUID.randomUUID());
        reg.setOem(new Oem("Fortinet"));
        reg.setOrganization(new Organization("Test Bank", Sector.BANK));
        reg.setCreatedBy(officer);

        when(dealRegistrationRepository.findById(reg.getId())).thenReturn(Optional.of(reg));
        when(auditLogRepository.findAll()).thenReturn(List.of(entry("DEAL_REGISTRATION", reg.getId(), "expiryDate")));

        service.run();

        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.KEY_DATE_CHANGED), eq(EntityType.DEAL_REGISTRATION), eq(reg.getId()), eq(officer), any());
        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.KEY_DATE_CHANGED), eq(EntityType.DEAL_REGISTRATION), eq(reg.getId()), eq(director), any());
    }

    @Test
    void aChangeAlreadyNotifiedIsNeverProcessedAgain() {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setLots(new ArrayList<>());
        AuditLogEntry alreadyHandled = entry("BID", bid.getId(), "closingAt");
        when(auditLogRepository.findAll()).thenReturn(List.of(alreadyHandled));
        AlertStateId key = new AlertStateId(EntityType.BID, bid.getId(), AlertKind.KEY_DATE_CHANGED, alreadyHandled.getId().toString());
        when(alertStateRepository.existsById(key)).thenReturn(true);

        service.run();

        verify(bidRepository, never()).findById(any());
        verify(notificationService, never()).sendIfNotAlreadySent(any(), any(), any(), any(), any());
        verify(alertStateRepository, never()).save(any());
    }
}
