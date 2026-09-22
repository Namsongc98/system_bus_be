package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.manage_revenue_ticket.Dto.request.UserRequestDto;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

    public void updatePassWord(@Valid UserRequestDto userRequest, String oldPassword){
        String email = userRequest.getEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với email: " + email));

        // Kiểm tra xem user đã có mật khẩu chưa
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("Mật khẩu cũ không đúng");
        }
        String newPassword = passwordEncoder.encode(userRequest.getPassword());
        // Mã hoá mật khẩu mới
        user.setPassword(newPassword);
        userRepository.save(user);
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
