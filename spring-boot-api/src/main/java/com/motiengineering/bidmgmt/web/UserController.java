package com.motiengineering.bidmgmt.web;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.dto.UserDto;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.security.CurrentUserContext;
import com.motiengineering.bidmgmt.service.BidMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AppUserRepository appUserRepository;
    private final BidMapper bidMapper;

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
}
