package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.dto.CreateUserRequest;
import com.motiengineering.bidmgmt.dto.DivisionDto;
import com.motiengineering.bidmgmt.dto.GrantLoginResult;
import com.motiengineering.bidmgmt.dto.UpdateUserRequest;
import com.motiengineering.bidmgmt.dto.UserAdminRowDto;
import com.motiengineering.bidmgmt.keycloakadmin.KeycloakUserProvisioningClient;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.UserDivisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers the two things most likely to go wrong: email-uniqueness on create, and divisions only ever applying to DIVISION_MANAGER. */
class UserServiceTest {

    private AppUserRepository appUserRepository;
    private UserDivisionRepository userDivisionRepository;
    private DivisionRepository divisionRepository;
    private BidMapper bidMapper;
    private KeycloakUserProvisioningClient keycloakUserProvisioningClient;
    private UserService service;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        userDivisionRepository = mock(UserDivisionRepository.class);
        divisionRepository = mock(DivisionRepository.class);
        bidMapper = mock(BidMapper.class);
        keycloakUserProvisioningClient = mock(KeycloakUserProvisioningClient.class);
        service = new UserService(appUserRepository, userDivisionRepository, divisionRepository, bidMapper, keycloakUserProvisioningClient);

        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
        when(userDivisionRepository.findById_UserId(any())).thenReturn(List.of());
        doNothing().when(userDivisionRepository).deleteById_UserId(any());
    }

    @Test
    void createRejectsADuplicateEmail() {
        when(appUserRepository.findByEmailIgnoreCase("taken@motiengineering.com")).thenReturn(Optional.of(new AppUser()));

        CreateUserRequest request = new CreateUserRequest("Taken Person", "taken@motiengineering.com", "SCOUT", null);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalArgumentException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void creatingAnAccountOfficerIgnoresAnySuppliedDivisionIds() {
        when(appUserRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        UUID divisionId = UUID.randomUUID();

        service.create(new CreateUserRequest("New Officer", "new.officer@motiengineering.com", "ACCOUNT_OFFICER", List.of(divisionId)));

        verify(divisionRepository, never()).findById(any());
        verify(userDivisionRepository, never()).save(any());
    }

    @Test
    void creatingADivisionManagerSavesAUserDivisionRowPerDivision() {
        when(appUserRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        UUID divisionId = UUID.randomUUID();
        Division division = new Division();
        division.setId(divisionId);
        division.setName("Security");
        when(divisionRepository.findById(divisionId)).thenReturn(Optional.of(division));
        when(bidMapper.toDto(any(Division.class))).thenReturn(new DivisionDto(divisionId, "SEC", "Security"));

        UserAdminRowDto result = service.create(new CreateUserRequest("New Manager", "new.manager@motiengineering.com", "DIVISION_MANAGER", List.of(divisionId)));

        assertThat(result.role()).isEqualTo("DIVISION_MANAGER");
        verify(userDivisionRepository).save(argThatMatchesDivision(divisionId));
    }

    @Test
    void grantLoginRefusesAUserWhoAlreadyHasOne() {
        AppUser existing = new AppUser();
        existing.setId(UUID.randomUUID());
        existing.setKeycloakUserId("already-has-one");
        when(appUserRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.grantLogin(existing.getId())).isInstanceOf(IllegalStateException.class);
        verify(keycloakUserProvisioningClient, never()).createUser(anyString(), anyString(), anyString());
    }

    @Test
    void grantLoginCreatesTheKeycloakUserAssignsTheRoleAndSetsATemporaryPassword() {
        AppUser existing = new AppUser();
        existing.setId(UUID.randomUUID());
        existing.setFullName("Selam Girma");
        existing.setEmail("selam.girma@motiengineering.com");
        existing.setRole(com.motiengineering.bidmgmt.domain.enums.Role.ACCOUNT_OFFICER);
        when(appUserRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(keycloakUserProvisioningClient.createUser("selam.girma@motiengineering.com", "Selam", "Girma")).thenReturn("kc-user-1");

        GrantLoginResult result = service.grantLogin(existing.getId());

        assertThat(result.temporaryPassword()).isNotBlank();
        assertThat(result.user().hasLogin()).isTrue();

        var order = inOrder(keycloakUserProvisioningClient);
        order.verify(keycloakUserProvisioningClient).createUser("selam.girma@motiengineering.com", "Selam", "Girma");
        order.verify(keycloakUserProvisioningClient).assignRealmRole("kc-user-1", "account_officer");
        order.verify(keycloakUserProvisioningClient).setTemporaryPassword(eq("kc-user-1"), eq(result.temporaryPassword()));
    }

    @Test
    void updateReplacesDivisionsRatherThanAccumulatingThem() {
        AppUser existing = new AppUser();
        existing.setId(UUID.randomUUID());
        existing.setRole(com.motiengineering.bidmgmt.domain.enums.Role.DIVISION_MANAGER);
        when(appUserRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        UUID newDivisionId = UUID.randomUUID();
        Division division = new Division();
        division.setId(newDivisionId);
        when(divisionRepository.findById(newDivisionId)).thenReturn(Optional.of(division));
        when(bidMapper.toDto(any(Division.class))).thenReturn(new DivisionDto(newDivisionId, "NET", "Network"));

        service.update(existing.getId(), new UpdateUserRequest("Updated Name", null, "DIVISION_MANAGER", List.of(newDivisionId), null));

        var order = inOrder(userDivisionRepository);
        order.verify(userDivisionRepository).deleteById_UserId(existing.getId());
        order.verify(userDivisionRepository).save(argThatMatchesDivision(newDivisionId));
    }

    @Test
    void grantLoginRefusesAUserWithNoEmailOnFile() {
        AppUser existing = new AppUser();
        existing.setId(UUID.randomUUID());
        existing.setFullName("Selam");
        existing.setEmail(null);
        existing.setRole(com.motiengineering.bidmgmt.domain.enums.Role.ACCOUNT_OFFICER);
        when(appUserRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.grantLogin(existing.getId())).isInstanceOf(IllegalStateException.class);
        verify(keycloakUserProvisioningClient, never()).createUser(anyString(), anyString(), anyString());
    }

    @Test
    void updateCanSetAnEmailForAUserWithNoLoginYet() {
        AppUser existing = new AppUser();
        existing.setId(UUID.randomUUID());
        existing.setEmail(null);
        existing.setRole(com.motiengineering.bidmgmt.domain.enums.Role.SCOUT);
        when(appUserRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(appUserRepository.findByEmailIgnoreCase("selam@motiengineering.com")).thenReturn(Optional.empty());

        UserAdminRowDto result = service.update(existing.getId(), new UpdateUserRequest("Selam", "selam@motiengineering.com", "SCOUT", null, null));

        assertThat(result.email()).isEqualTo("selam@motiengineering.com");
    }

    @Test
    void updateNeverChangesEmailOnceAUserAlreadyHasALogin() {
        AppUser existing = new AppUser();
        existing.setId(UUID.randomUUID());
        existing.setEmail("original@motiengineering.com");
        existing.setKeycloakUserId("kc-user-existing");
        existing.setRole(com.motiengineering.bidmgmt.domain.enums.Role.SCOUT);
        when(appUserRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        UserAdminRowDto result = service.update(existing.getId(), new UpdateUserRequest("Selam", "attempted-new@motiengineering.com", "SCOUT", null, null));

        assertThat(result.email()).isEqualTo("original@motiengineering.com");
        verify(appUserRepository, never()).findByEmailIgnoreCase(anyString());
    }

    private static com.motiengineering.bidmgmt.domain.UserDivision argThatMatchesDivision(UUID divisionId) {
        return org.mockito.ArgumentMatchers.argThat(ud -> ud.getId().getDivisionId().equals(divisionId));
    }
}
