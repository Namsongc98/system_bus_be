package com.ticket_system.manage_revenue_ticket.Dto.request;

import com.ticket_system.manage_revenue_ticket.Enum.RouteStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RouteRequestDto {

    @NotBlank(message = "Tên tuyến không được để trống")
    @Size(max = 100, message = "Tên tuyến tối đa 100 ký tự")
    private String routeName;

    @NotBlank(message = "Điểm đi không được để trống")
    @Size(max = 100, message = "Điểm đi tối đa 100 ký tự")
    private String startPoint;

    @NotBlank(message = "Điểm đến không được để trống")
    @Size(max = 100, message = "Điểm đến tối đa 100 ký tự")
    private String endPoint;

    @NotNull(message = "Quãng đường không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Quãng đường phải lớn hơn 0")
    @Digits(integer = 4, fraction = 2, message = "Quãng đường tối đa 9999.99 km, 2 chữ số thập phân")
    private BigDecimal distanceKm;

    @NotNull(message = "Trạng thái tuyến không được để trống")
    private RouteStatus status = RouteStatus.ACTIVE; // Mặc định ACTIVE
}
