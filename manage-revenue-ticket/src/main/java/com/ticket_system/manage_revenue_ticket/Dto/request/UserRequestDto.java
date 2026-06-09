package com.ticket_system.manage_revenue_ticket.Dto.request;

import com.ticket_system.common.Enum.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRequestDto {

    private Long id;

    private UserRole role;     // optional

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;  // required

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password; // required
}
