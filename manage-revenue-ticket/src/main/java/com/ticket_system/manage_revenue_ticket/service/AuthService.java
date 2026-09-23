package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.request.UserRequestDto;
import com.ticket_system.manage_revenue_ticket.Dto.request.UserUpdatePasswordRequestDto;
import com.ticket_system.manage_revenue_ticket.Dto.response.CurrentUserResponse;
import com.ticket_system.manage_revenue_ticket.entity.Profile;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.repository.ProfileRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final ProfileRepository profileRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, ProfileRepository profileRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
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

        User checkUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if(passwordEncoder.matches(password, checkUser.getPassword())){
            return checkUser;
        }else {
            throw new RuntimeException("Invalid email or password");
        }
    }
}
