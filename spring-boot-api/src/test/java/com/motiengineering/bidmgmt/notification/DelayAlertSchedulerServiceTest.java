package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AlertState;
import com.motiengineering.bidmgmt.domain.AlertStateId;
import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.FollowUpState;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.LotAccountOfficerId;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.UserDivisionId;
import com.motiengineering.bidmgmt.domain.enums.AlertKind;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.IndicatorLevel;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.repository.AlertStateRepository;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.UserDivisionRepository;
import com.motiengineering.bidmgmt.service.FollowUpService;
import com.motiengineering.bidmgmt.service.IndicatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers single-fire-per-threshold-crossing, escalation recipients (amber/red/critical), and snooze suppression. */
class DelayAlertSchedulerServiceTest {

    private BidRepository bidRepository;
    private LotAccountOfficerRepository lotAccountOfficerRepository;
    private UserDivisionRepository userDivisionRepository;
    private AppUserRepository appUserRepository;
    private AlertStateRepository alertStateRepository;
    private IndicatorService indicatorService;
    private FollowUpService followUpService;
    private NotificationService notificationService;
    private DelayAlertSchedulerService service;

    private Bid bid;
    private BidLot lot;
    private AppUser officer;
    private AppUser manager;
    private AppUser director;

    @BeforeEach
    void setUp() throws Exception {
        bidRepository = mock(BidRepository.class);
        lotAccountOfficerRepository = mock(LotAccountOfficerRepository.class);
        userDivisionRepository = mock(UserDivisionRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        alertStateRepository = mock(AlertStateRepository.class);
        indicatorService = mock(IndicatorService.class);
        followUpService = mock(FollowUpService.class);
        notificationService = mock(NotificationService.class);

        service = new DelayAlertSchedulerService(bidRepository, lotAccountOfficerRepository, userDivisionRepository,
                appUserRepository, alertStateRepository, indicatorService, followUpService, notificationService);
        setField(service, "directorEmail", "director@example.com");
        setField(service, "appBaseUrl", "http://localhost:3002");

        Organization org = new Organization("Test Bank", Sector.BANK);
        bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setTitle("Test Bid");
        bid.setOrganization(org);
        bid.setCreatedAt(Instant.now());

        Division division = new Division();
        division.setId(UUID.randomUUID());
        division.setName("Server & Storage");

        lot = new BidLot();
        lot.setId(UUID.randomUUID());
        lot.setBid(bid);
        lot.setLotLabel("Lot 1");
        lot.setDivision(division);
        lot.setStatus(BidStatus.UNDER_REVIEW);
        lot.setCreatedAt(Instant.now());

        List<BidLot> lots = new ArrayList<>();
        lots.add(lot);
        bid.setLots(lots);

        when(bidRepository.findAll()).thenReturn(List.of(bid));
        when(followUpService.isSnoozed(any(), any(), any())).thenReturn(false);
        when(followUpService.get(any(), any())).thenReturn(Optional.empty());
        when(alertStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        officer = userWithRole(Role.ACCOUNT_OFFICER, "officer@example.com");
        manager = userWithRole(Role.DIVISION_MANAGER, "manager@example.com");
        director = userWithRole(Role.DIRECTOR, "director@example.com");

        LotAccountOfficer loa = new LotAccountOfficer(new LotAccountOfficerId(lot.getId(), officer.getId()));
        when(lotAccountOfficerRepository.findById_LotId(lot.getId())).thenReturn(List.of(loa));
        when(appUserRepository.findById(officer.getId())).thenReturn(Optional.of(officer));

        UserDivision userDivision = new UserDivision(new UserDivisionId(manager.getId(), division.getId()), true);
        when(userDivisionRepository.findById_DivisionId(division.getId())).thenReturn(List.of(userDivision));
        when(appUserRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(appUserRepository.findByEmailIgnoreCase("director@example.com")).thenReturn(Optional.of(director));
    }

    private AppUser userWithRole(Role role, String email) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setFullName(role.name());
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private void indicatorReturns(IndicatorLevel level, boolean stale) {
        when(indicatorService.compute(eq(EntityType.LOT), eq(lot.getId()), any(), any(), any(), any(), any()))
                .thenReturn(new IndicatorService.IndicatorResult(level, 80.0, 1.6, 2.0, true, stale));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void amberCrossingNotifiesOnlyTheOfficerWhenTheyOptedIntoImmediateAmberAlerts() {
        officer.setAmberImmediate(true);
        indicatorReturns(IndicatorLevel.AMBER, false);
        when(alertStateRepository.findById(any())).thenReturn(Optional.empty());

        service.run();

        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.DELAY_AMBER), eq(EntityType.LOT), eq(lot.getId()), eq(officer), any());
        verify(notificationService, never()).sendIfNotAlreadySent(eq(ReminderType.DELAY_AMBER), eq(EntityType.LOT), eq(lot.getId()), eq(manager), any());
        verify(alertStateRepository).save(any(AlertState.class));
    }

    @Test
    void amberCrossingRecordsTheEpisodeButSkipsImmediateSendByDefault() {
        // officer.amberImmediate defaults to false - amber is digest-only unless opted in.
        indicatorReturns(IndicatorLevel.AMBER, false);
        when(alertStateRepository.findById(any())).thenReturn(Optional.empty());

        service.run();

        verify(notificationService, never()).sendIfNotAlreadySent(eq(ReminderType.DELAY_AMBER), any(), any(), any(), any());
        verify(alertStateRepository).save(any(AlertState.class));
    }

    @Test
    void redCrossingNotifiesOfficerAndManagerButNotDirector() {
        indicatorReturns(IndicatorLevel.RED, false);
        when(alertStateRepository.findById(any())).thenReturn(Optional.empty());

        service.run();

        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.DELAY_RED), eq(EntityType.LOT), eq(lot.getId()), eq(officer), any());
        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.DELAY_RED), eq(EntityType.LOT), eq(lot.getId()), eq(manager), any());
        verify(notificationService, never()).sendIfNotAlreadySent(eq(ReminderType.DELAY_RED), eq(EntityType.LOT), eq(lot.getId()), eq(director), any());
    }

    @Test
    void criticalCrossingNotifiesOfficerManagerAndDirector() {
        indicatorReturns(IndicatorLevel.CRITICAL, false);
        when(alertStateRepository.findById(any())).thenReturn(Optional.empty());

        service.run();

        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.DELAY_CRITICAL), eq(EntityType.LOT), eq(lot.getId()), eq(officer), any());
        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.DELAY_CRITICAL), eq(EntityType.LOT), eq(lot.getId()), eq(manager), any());
        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.DELAY_CRITICAL), eq(EntityType.LOT), eq(lot.getId()), eq(director), any());
    }

    @Test
    void sameLevelDoesNotFireTwice() {
        indicatorReturns(IndicatorLevel.RED, false);
        AlertStateId key = new AlertStateId(EntityType.LOT, lot.getId(), AlertKind.INDICATOR, "UNDER_REVIEW");
        when(alertStateRepository.findById(key)).thenReturn(Optional.of(new AlertState(key, "RED", Instant.now())));

        service.run();

        verify(notificationService, never()).sendIfNotAlreadySent(eq(ReminderType.DELAY_RED), any(), any(), any(), any());
        verify(alertStateRepository, never()).save(any());
    }

    @Test
    void escalationFromAmberToRedFiresAgain() {
        indicatorReturns(IndicatorLevel.RED, false);
        AlertStateId key = new AlertStateId(EntityType.LOT, lot.getId(), AlertKind.INDICATOR, "UNDER_REVIEW");
        when(alertStateRepository.findById(key)).thenReturn(Optional.of(new AlertState(key, "AMBER", Instant.now())));

        service.run();

        verify(notificationService, times(1)).sendIfNotAlreadySent(eq(ReminderType.DELAY_RED), eq(EntityType.LOT), eq(lot.getId()), eq(officer), any());
    }

    @Test
    void deEscalationBelowAmberClearsStateInsteadOfSending() {
        indicatorReturns(IndicatorLevel.GREEN, false);

        service.run();

        verify(notificationService, never()).sendIfNotAlreadySent(any(), eq(EntityType.LOT), eq(lot.getId()), any(), any());
        verify(alertStateRepository).deleteById_EntityTypeAndId_EntityIdAndId_AlertKind(EntityType.LOT, lot.getId(), AlertKind.INDICATOR);
    }

    @Test
    void snoozedLotIsSkippedEntirely() {
        indicatorReturns(IndicatorLevel.CRITICAL, false);
        when(followUpService.isSnoozed(eq(EntityType.LOT), eq(lot.getId()), any())).thenReturn(true);

        service.run();

        verify(notificationService, never()).sendIfNotAlreadySent(any(), eq(EntityType.LOT), eq(lot.getId()), any(), any());
        verify(alertStateRepository, never()).save(any());
    }

    @Test
    void followUpDueFiresOnceToTheFollowUpOwnerOnly() {
        indicatorReturns(IndicatorLevel.GREEN, false);
        FollowUpState state = new FollowUpState(EntityType.LOT, lot.getId());
        state.setNextFollowUpDate(java.time.LocalDate.now(ZoneIdAddis()).minusDays(1));
        state.setFollowUpOwner(officer);
        when(followUpService.get(EntityType.LOT, lot.getId())).thenReturn(Optional.of(state));
        when(alertStateRepository.findById(any())).thenReturn(Optional.empty());

        service.run();

        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.FOLLOWUP_DUE), eq(EntityType.LOT), eq(lot.getId()), eq(officer), any());
    }

    private static java.time.ZoneId ZoneIdAddis() {
        return java.time.ZoneId.of("Africa/Addis_Ababa");
    }
}
