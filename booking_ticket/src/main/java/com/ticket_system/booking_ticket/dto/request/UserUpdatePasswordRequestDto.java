package com.ticket_system.booking_ticket.dto.request;


import com.ticket_system.booking_ticket.dto.request.UserRequestDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserUpdatePasswordRequestDto extends UserRequestDto {

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String oldPassword;


}
