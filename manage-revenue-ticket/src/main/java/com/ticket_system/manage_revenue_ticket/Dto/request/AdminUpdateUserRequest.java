package com.ticket_system.manage_revenue_ticket.Dto.request;

import com.ticket_system.common.Enum.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Body of PUT /api/user/{id}. Email, password and active are changed elsewhere, never here. */
@Setter
@Getter
public class AdminUpdateUserRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String phone;

    @NotNull(message = "Vai trò không được để trống")
    private UserRole role;
}
