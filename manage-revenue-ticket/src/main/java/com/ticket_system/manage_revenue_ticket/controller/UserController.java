package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.manage_revenue_ticket.Dto.request.AdminCreateUserRequest;
import com.ticket_system.manage_revenue_ticket.Dto.request.AdminUpdateUserRequest;
import com.ticket_system.manage_revenue_ticket.Dto.request.UserStatusRequest;
import com.ticket_system.manage_revenue_ticket.Dto.response.UserCountsResponse;
import com.ticket_system.manage_revenue_ticket.Dto.response.UserResponse;
import com.ticket_system.manage_revenue_ticket.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Admin user management. "id" (the caller) is set from the JWT by AuthInterceptor. */
@RestController
@RequestMapping("/api/user")
@RoleRequired(UserRole.ADMIN)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<PageResponse<UserResponse>>> getUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Get Successfully",
                userService.getUsers(role, keyword, page, size)));
    }

    @GetMapping("/counts")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<UserCountsResponse>> getCounts() {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Get Successfully", userService.getCounts()));
    }

    @GetMapping("/{userId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<UserResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Get Successfully", userService.getUser(userId)));
    }

    @PostMapping
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<UserResponse>> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponseDto.success(201, "Post successfully", userService.create(request)));
    }

    @PutMapping("/{userId}")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<UserResponse>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUserRequest request,
            @RequestAttribute("id") Long callerId) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Put successfully",
                userService.update(userId, request, callerId)));
    }

    @PutMapping("/{userId}/status")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<BaseResponseDto<UserResponse>> setStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UserStatusRequest request,
            @RequestAttribute("id") Long callerId) {
        return ResponseEntity.ok(BaseResponseDto.success(200, "Put successfully",
                userService.setActive(userId, request.getActive(), callerId)));
    }
}
