package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Bid;
import com.motiengineering.bidmgmt.domain.BidLot;
import com.motiengineering.bidmgmt.domain.DealRegistration;
import com.motiengineering.bidmgmt.domain.LotAccountOfficer;
import com.motiengineering.bidmgmt.domain.LotAccountOfficerId;
import com.motiengineering.bidmgmt.domain.Oem;
import com.motiengineering.bidmgmt.domain.Organization;
import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.UserDivisionId;
import com.motiengineering.bidmgmt.domain.enums.BidStatus;
import com.motiengineering.bidmgmt.domain.enums.Currency;
import com.motiengineering.bidmgmt.domain.enums.DealRegistrationStatus;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.domain.enums.Sector;
import com.motiengineering.bidmgmt.dto.DashboardDto;
import com.motiengineering.bidmgmt.repository.ActivityLogRepository;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.BidRepository;
import com.motiengineering.bidmgmt.repository.BidStatusHistoryRepository;
import com.motiengineering.bidmgmt.repository.DealRegistrationRepository;
import com.motiengineering.bidmgmt.repository.LotAccountOfficerRepository;
import com.motiengineering.bidmgmt.repository.UserDivisionRepository;
import com.motiengineering.bidmgmt.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers the exact-day-match reminder rules from the brief - the easiest place to silently drift to "every day from N onward". */
class ReminderSchedulerServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private BidRepository bidRepository;
    private DealRegistrationRepository dealRegistrationRepository;
    private LotAccountOfficerRepository lotAccountOfficerRepository;
    private UserDivisionRepository userDivisionRepository;
    private AppUserRepository appUserRepository;
    private ActivityLogRepository activityLogRepository;
    private BidStatusHistoryRepository bidStatusHistoryRepository;
    private DashboardService dashboardService;
    private NotificationService notificationService;
    private ReminderSchedulerService scheduler;

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        dealRegistrationRepository = mock(DealRegistrationRepository.class);
        lotAccountOfficerRepository = mock(LotAccountOfficerRepository.class);
        userDivisionRepository = mock(UserDivisionRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        activityLogRepository = mock(ActivityLogRepository.class);
        bidStatusHistoryRepository = mock(BidStatusHistoryRepository.class);
        dashboardService = mock(DashboardService.class);
        notificationService = mock(NotificationService.class);

        when(bidRepository.findAll()).thenReturn(List.of());
        when(dealRegistrationRepository.findAll()).thenReturn(List.of());
        when(lotAccountOfficerRepository.findById_LotId(any())).thenReturn(List.of());
        when(userDivisionRepository.findById_DivisionId(any())).thenReturn(List.of());
        when(activityLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(bidStatusHistoryRepository.findByChangedAtBetweenOrderByChangedAt(any(), any())).thenReturn(List.of());
        when(dashboardService.build()).thenReturn(mock(DashboardDto.class));

        scheduler = new ReminderSchedulerService(
                bidRepository, dealRegistrationRepository, lotAccountOfficerRepository, userDivisionRepository,
                appUserRepository, activityLogRepository, bidStatusHistoryRepository, dashboardService, notificationService);
        setField("directorEmail", "mengistu.redae@motiengineering.com");
        setField("appBaseUrl", "http://localhost:3002");
    }

    private void setField(String name, String value) {
        try {
            var field = ReminderSchedulerService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(scheduler, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Organization organization() {
        return new Organization("Test Bank", Sector.BANK);
    }

    private AppUser officer(String email) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFullName("Officer");
        user.setRole(Role.ACCOUNT_OFFICER);
        return user;
    }

    /** Stubs the officer-lookup chain so recipientsForLot(lot) resolves to exactly this one officer. */
    private void assignOfficer(BidLot lot) {
        AppUser officer = officer("officer@motiengineering.com");
        when(lotAccountOfficerRepository.findById_LotId(lot.getId()))
                .thenReturn(List.of(new LotAccountOfficer(new LotAccountOfficerId(lot.getId(), officer.getId()))));
        when(appUserRepository.findById(officer.getId())).thenReturn(java.util.Optional.of(officer));
    }

    private Bid bidClosingInDays(long days) {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setTitle("Core banking refresh");
        bid.setOrganization(organization());
        bid.setStatus(BidStatus.PREPARING);
        bid.setClosingAt(Instant.now().atZone(ZONE).toLocalDate().plusDays(days).atStartOfDay(ZONE).toInstant().plus(12, ChronoUnit.HOURS));
        BidLot lot = new BidLot();
        lot.setId(UUID.randomUUID());
        lot.setBid(bid);
        bid.setLots(new ArrayList<>(List.of(lot)));
        assignOfficer(lot);
        return bid;
    }

    @Test
    void sendsSevenDayReminderExactlyAtSevenDaysOut() {
        Bid bid = bidClosingInDays(7);
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        scheduler.run();

        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.BID_CLOSING_7D), eq(EntityType.BID), any(), any(), any());
    }

    @Test
    void doesNotSendClosingReminderFiveDaysOut() {
        Bid bid = bidClosingInDays(5);
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        scheduler.run();

        verify(notificationService, never()).sendIfNotAlreadySent(
                ArgumentMatchers.argThat(t -> t == ReminderType.BID_CLOSING_7D || t == ReminderType.BID_CLOSING_3D || t == ReminderType.BID_CLOSING_1D),
                eq(EntityType.BID), any(), any(), any());
    }

    @Test
    void terminalBidsAreNeverReminded() {
        Bid bid = bidClosingInDays(3);
        bid.setStatus(BidStatus.WON);
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        scheduler.run();

        verify(notificationService, never()).sendIfNotAlreadySent(any(), eq(EntityType.BID), any(), any(), any());
    }

    @Test
    void sendsClarificationReminderExactlyTwoDaysOut() {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setTitle("Core banking refresh");
        bid.setOrganization(organization());
        bid.setStatus(BidStatus.PREPARING);
        bid.setClarificationDeadline(Instant.now().atZone(ZONE).toLocalDate().plusDays(2).atStartOfDay(ZONE).toInstant().plus(12, ChronoUnit.HOURS));
        BidLot lot = new BidLot();
        lot.setId(UUID.randomUUID());
        lot.setBid(bid);
        bid.setLots(new ArrayList<>(List.of(lot)));
        assignOfficer(lot);
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        scheduler.run();

        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.CLARIFICATION_2D), eq(EntityType.BID), eq(bid.getId()), any(), any());
    }

    private DealRegistration dealRegistrationExpiringInDays(long days) {
        DealRegistration reg = new DealRegistration();
        reg.setId(UUID.randomUUID());
        Oem oem = new Oem();
        oem.setName("Cisco");
        reg.setOem(oem);
        reg.setOrganization(organization());
        reg.setStatus(DealRegistrationStatus.APPROVED);
        reg.setExpiryDate(java.time.LocalDate.now(ZONE).plusDays(days));
        AppUser createdBy = officer("officer@motiengineering.com");
        reg.setCreatedBy(createdBy);
        return reg;
    }

    @Test
    void sendsDealRegistrationExpiryReminderToDirectorAndCreator() {
        DealRegistration reg = dealRegistrationExpiringInDays(30);
        when(dealRegistrationRepository.findAll()).thenReturn(List.of(reg));
        AppUser director = officer("mengistu.redae@motiengineering.com");
        director.setRole(Role.DIRECTOR);
        when(appUserRepository.findByEmailIgnoreCase("mengistu.redae@motiengineering.com")).thenReturn(java.util.Optional.of(director));

        scheduler.run();

        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.DEAL_REGISTRATION_EXPIRY_30D), eq(EntityType.DEAL_REGISTRATION), eq(reg.getId()), eq(director), any());
        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.DEAL_REGISTRATION_EXPIRY_30D), eq(EntityType.DEAL_REGISTRATION), eq(reg.getId()), eq(reg.getCreatedBy()), any());
    }

    @Test
    void expiredDealRegistrationsAreNeverReminded() {
        DealRegistration reg = dealRegistrationExpiringInDays(7);
        reg.setStatus(DealRegistrationStatus.EXPIRED);
        when(dealRegistrationRepository.findAll()).thenReturn(List.of(reg));

        scheduler.run();

        verify(notificationService, never()).sendIfNotAlreadySent(any(), eq(EntityType.DEAL_REGISTRATION), any(), any(), any());
    }

    @Test
    void sendsBidBondReminderExactlyThirtyDaysAfterOpeningWhenStillOutstanding() {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setTitle("Core banking refresh");
        bid.setOrganization(organization());
        bid.setStatus(BidStatus.SUBMITTED);
        bid.setOpeningAt(Instant.now().atZone(ZONE).toLocalDate().minusDays(30).atStartOfDay(ZONE).toInstant());
        BidLot lot = new BidLot();
        lot.setId(UUID.randomUUID());
        lot.setBid(bid);
        lot.setBidBondAmount(new java.math.BigDecimal("50000"));
        lot.setBidBondCurrency(Currency.ETB);
        lot.setBidBondReturned(false);
        bid.setLots(new ArrayList<>(List.of(lot)));
        assignOfficer(lot);
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        scheduler.run();

        verify(notificationService).sendIfNotAlreadySent(eq(ReminderType.BID_BOND_NOT_RETURNED_30D), eq(EntityType.LOT), eq(lot.getId()), any(), any());
    }

    @Test
    void returnedBondsAreNeverReminded() {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setTitle("Core banking refresh");
        bid.setOrganization(organization());
        bid.setStatus(BidStatus.SUBMITTED);
        bid.setOpeningAt(Instant.now().atZone(ZONE).toLocalDate().minusDays(30).atStartOfDay(ZONE).toInstant());
        BidLot lot = new BidLot();
        lot.setId(UUID.randomUUID());
        lot.setBid(bid);
        lot.setBidBondAmount(new java.math.BigDecimal("50000"));
        lot.setBidBondCurrency(Currency.ETB);
        lot.setBidBondReturned(true);
        bid.setLots(new ArrayList<>(List.of(lot)));
        when(bidRepository.findAll()).thenReturn(List.of(bid));

        scheduler.run();

        verify(notificationService, never()).sendIfNotAlreadySent(eq(ReminderType.BID_BOND_NOT_RETURNED_30D), any(), any(), any(), any());
    }

    @Test
    void directorDigestIsAlwaysSentOnceAsLongAsDirectorIsResolvable() {
        AppUser director = officer("mengistu.redae@motiengineering.com");
        director.setRole(Role.DIRECTOR);
        when(appUserRepository.findByEmailIgnoreCase("mengistu.redae@motiengineering.com")).thenReturn(java.util.Optional.of(director));

        scheduler.run();

        verify(notificationService, times(1)).sendIfNotAlreadySent(eq(ReminderType.DIRECTOR_DIGEST), any(), eq(director.getId()), eq(director), any());
    }

    @Test
    void directorDigestIsSkippedWhenDirectorEmailDoesNotResolveToAnAppUser() {
        when(appUserRepository.findByEmailIgnoreCase(any())).thenReturn(java.util.Optional.empty());

        scheduler.run();

        verify(notificationService, never()).sendIfNotAlreadySent(eq(ReminderType.DIRECTOR_DIGEST), any(), any(), any(), any());
    }
}
