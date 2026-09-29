package com.ticket_system.manage_revenue_ticket.Dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Body of PUT /api/user/{id}/status: false locks the account, true unlocks it. */
@Setter
@Getter
public class UserStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private Boolean active;
}
