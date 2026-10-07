package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.dto.CreateUserRequest;
import com.motiengineering.bidmgmt.dto.GrantLoginResult;
import com.motiengineering.bidmgmt.dto.UpdateUserRequest;
import com.motiengineering.bidmgmt.dto.UserAdminRowDto;
import com.motiengineering.bidmgmt.dto.UserDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.BidMapper;
import com.motiengineering.bidmgmt.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AppUserRepository appUserRepository;
    private final BidMapper bidMapper;
    private final UserService userService;

    @GetMapping
    public List<UserDto> list(@RequestParam(required = false) String role) {
        List<AppUser> users = appUserRepository.findByActiveTrue();
        if (role != null) {
            Role roleEnum = Role.valueOf(role.toUpperCase());
            users = users.stream().filter(u -> u.getRole() == roleEnum).toList();
        }
        return users.stream().map(bidMapper::toUserDto).toList();
    }

    @GetMapping("/me")
    public UserDto me() {
        return bidMapper.toUserDto(CurrentUserContext.requireUser());
    }

    /** Every user, active or not, with full admin detail - Director-only, for the user management page. */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<UserAdminRowDto> adminList() {
        return userService.list();
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<UserAdminRowDto> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('DIRECTOR')")
    public UserAdminRowDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PostMapping("/admin/{id}/grant-login")
    @PreAuthorize("hasRole('DIRECTOR')")
    public GrantLoginResult grantLogin(@PathVariable UUID id) {
        return userService.grantLogin(id);
    }

    @PostMapping("/admin/{id}/deactivate")
    @PreAuthorize("hasRole('DIRECTOR')")
    public UserAdminRowDto deactivate(@PathVariable UUID id) {
        return userService.setActive(id, false);
    }

    @PostMapping("/admin/{id}/reactivate")
    @PreAuthorize("hasRole('DIRECTOR')")
    public UserAdminRowDto reactivate(@PathVariable UUID id) {
        return userService.setActive(id, true);
    }
}
