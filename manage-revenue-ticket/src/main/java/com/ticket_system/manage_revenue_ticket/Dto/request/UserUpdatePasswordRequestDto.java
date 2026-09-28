package com.ticket_system.manage_revenue_ticket.Dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// No email/id field: the account is always the caller's, taken from the JWT.
@Getter
@Setter
@NoArgsConstructor
public class UserUpdatePasswordRequestDto {

    @NotBlank(message = "Old password is required")
    private String oldPassword;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
