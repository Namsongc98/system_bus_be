package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.AccountLockedException;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.request.UserRequestDto;
import com.ticket_system.manage_revenue_ticket.Dto.request.UserUpdatePasswordRequestDto;
import com.ticket_system.manage_revenue_ticket.Dto.response.CurrentUserResponse;
import com.ticket_system.manage_revenue_ticket.entity.Profile;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.repository.ProfileRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "Email hoặc mật khẩu không đúng";

    private final UserRepository userRepository;

    private final ProfileRepository profileRepository;

    private final BCryptPasswordEncoder passwordEncoder;

    // B37 b: a login with an unknown email still runs one BCrypt check (against this random hash), so the
    // response time does not tell which emails are registered.
    private final String unknownUserHash;

    @Autowired
    public AuthService(UserRepository userRepository, ProfileRepository profileRepository) {
        this(userRepository, profileRepository, new BCryptPasswordEncoder());
    }

    AuthService(UserRepository userRepository, ProfileRepository profileRepository,
                BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // Shared with UserService so every password is hashed by the same encoder (B16 moves it to a bean).
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public User register(@Valid UserRequestDto user) {

        String email = user.getEmail();

        if(userRepository.existsByEmail(email)){
            throw new IllegalArgumentException("Email already exists");
        }

        String password = passwordEncoder.encode(user.getPassword());

        try{
            User newUser = User.builder()
                    .email(email)
                    .password(password)
                    // Public register is CUSTOMER-only: any role in the request body is ignored.
                    .role(UserRole.CUSTOMER)
                    .createdAt(LocalDateTime.now())
                    .build();
            return userRepository.save(newUser);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    // userId comes from the caller's JWT, never from the request body: a user can only change their own password.
    public void updatePassWord(Long userId, UserUpdatePasswordRequestDto request){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu cũ không đúng");
        }
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
    }

    // userId comes from the caller's JWT. fullName/phone stay null until the user has a profile.
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        Profile profile = profileRepository.findByUserId(userId).orElse(null);
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                profile == null ? null : profile.getFullName(),
                profile == null ? null : profile.getPhone());
    }

    public User login(@Valid UserRequestDto user){
        String email = user.getEmail();
        String password = user.getPassword();

        // B26: unknown email and wrong password give the same 401, so the response does not reveal
        // which emails are registered.
        User checkUser = userRepository.findByEmail(email).orElse(null);
        if (checkUser == null) {
            passwordEncoder.matches(password, unknownUserHash);
            throw new SecurityException(INVALID_CREDENTIALS);
        }
        if(passwordEncoder.matches(password, checkUser.getPassword())){
            // Checked after the password so a caller without it cannot learn the account is locked.
            if (Boolean.FALSE.equals(checkUser.getIsActive())) {
                throw new AccountLockedException();
            }
            return checkUser;
        }else {
            throw new SecurityException(INVALID_CREDENTIALS);
        }
    }
}
