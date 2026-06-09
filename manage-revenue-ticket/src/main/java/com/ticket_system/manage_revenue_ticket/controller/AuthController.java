package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.Dto.response.TokenResponse;
import com.ticket_system.common.annotation.PublicApi;
import com.ticket_system.manage_revenue_ticket.Dto.request.UserRequestDto;
import com.ticket_system.manage_revenue_ticket.Dto.request.UserSession;
import com.ticket_system.manage_revenue_ticket.Dto.request.UserUpdatePasswordRequestDto;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.service.AuthService;
import com.ticket_system.manage_revenue_ticket.service.UserService;
import com.ticket_system.common.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @Autowired
    UserService userService;

    @Autowired
    AuthService authService;

    @Autowired
    JwtUtil jwtUtil;

    @PostMapping("/register")
    @PublicApi
    public ResponseEntity<BaseResponseDto<TokenResponse>> register(@Valid @RequestBody UserRequestDto req){
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPassword(req.getPassword());
        user.setRole(req.getRole());
        User savedUser = authService.register(req);
        String accessToken = jwtUtil.generateAccessToken(savedUser.getId(),savedUser.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(savedUser.getId(),savedUser.getRole());
        TokenResponse token = new TokenResponse(accessToken, refreshToken);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponseDto.success(201, "Register successfully", token));
    }
    @PutMapping("/update-password")
    public ResponseEntity<BaseResponseDto<TokenResponse>> updatePass(@RequestBody UserUpdatePasswordRequestDto req){
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPassword(req.getPassword());
        String oldPassword = req.getOldPassword();
        authService.updatePassWord(req, oldPassword);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponseDto.success(201, "Update Password successfully",null));
    }

    private final RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/login")
    @PublicApi
    public ResponseEntity<BaseResponseDto<TokenResponse>> login(@RequestBody UserRequestDto req){
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPassword(req.getPassword());
        user.setRole(req.getRole());
        User loginUser = authService.login(req);
        String accessToken = jwtUtil.generateAccessToken(loginUser.getId(),loginUser.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(loginUser.getId(),loginUser.getRole());

      String sessionKey = "session:" + loginUser.getId();

      UserSession sessionData = new UserSession();
      sessionData.setUserId(loginUser.getId());
      sessionData.setEmail(loginUser.getEmail());
      sessionData.setRole(loginUser.getRole().toString());
      sessionData.setLoginTime(LocalDateTime.now());

      // Lưu 7 ngày (ví dụ)
      redisTemplate.opsForValue().set(
        sessionKey,
        sessionData,
        1,
        TimeUnit.DAYS
      );
        TokenResponse token = new TokenResponse(accessToken, refreshToken);
        return ResponseEntity.ok(BaseResponseDto.success(200,"Login successfully",token));
    }
}
