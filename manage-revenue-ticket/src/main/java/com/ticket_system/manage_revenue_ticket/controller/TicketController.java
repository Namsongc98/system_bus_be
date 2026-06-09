package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Dto.request.TicketRequestDto;
import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.annotation.PublicApi;

import com.ticket_system.manage_revenue_ticket.entity.Ticket;
import com.ticket_system.manage_revenue_ticket.service.FileService;
import com.ticket_system.manage_revenue_ticket.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/ticket")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private FileService excelService;

    @PostMapping
    public ResponseEntity<BaseResponseDto<Ticket>> addTicket(@RequestBody TicketRequestDto responseDto){
        Ticket ticket = ticketService.createTicket(responseDto);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Add Successfully",ticket));
    };
    // export excel tổng doang thu của 1 xe theo time
    @GetMapping("/summary/excel")
    public ResponseEntity<byte[]> exportTicketSummaryToExcel(
            @RequestParam(required = false) Long busId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd:HH:mm:ss") LocalDateTime fromDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd:HH:mm:ss") LocalDateTime toDate) {

        try {
            List<Map<String, Object>> summaryData = ticketService.getTicketSummaryByBusAndTime(busId, fromDate, toDate);

            // Tạo file Excel từ dữ liệu
            ByteArrayOutputStream outputStream = excelService.exportTicketSummaryToExcel(summaryData);

            // Thiết lập các header cho response
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "ticket_summary.xlsx");

            return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);

        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // sửa ticket
    @PutMapping("/{tripId}")
    public ResponseEntity<BaseResponseDto<Ticket>> updateTicket(@PathVariable String tripId, @RequestBody TicketRequestDto responseDto){
        ticketService.updateTicket(Long.parseLong(tripId), responseDto);
        return ResponseEntity.ok(BaseResponseDto.success(201,"update Successfully",null));
    }

    // ticket tổng doang thu của 1 xe theo time
    @GetMapping("/summary")
    public ResponseEntity<BaseResponseDto<?>> getTicketSummaryByBusAndTime(
            @RequestParam(required = false) Long busId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        List<Map<String, Object>> result = ticketService.getTicketSummaryByBusAndTime(busId, fromDate, toDate);
        return ResponseEntity.ok(BaseResponseDto.success(201,"Get Successfully",result));
    }

    @PostMapping("/loyalty_reward/{idReward}")
    public  ResponseEntity<BaseResponseDto<Ticket>> createTicketByLoyalty(
            @RequestBody TicketRequestDto responseDto,
            @PathVariable Long idReward
    ){
        Ticket ticket =  ticketService.createTicketByLoyalty(responseDto, idReward);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponseDto.success(201, "Add Successfully", ticket));
    }
  @GetMapping("/email")
  @PublicApi
  public ResponseEntity<?> confirmTicket(@RequestParam String ticketId, @RequestParam String status) {
    try {
      // 1. Logic xử lý database tại đây
      System.out.println("Processing ticket: " + ticketId + " with status: " + status);

      // 2. Trả về thông báo thành công
      Map<String, String> response = new HashMap<>();
      response.put("message", "Cập nhật trạng thái ticket thành công!");
      return ResponseEntity.ok(response);

    } catch (Exception e) {
      return ResponseEntity.status(500).body("Có lỗi xảy ra: " + e.getMessage());
    }
  }
  @PostMapping("/confirm")
  @PublicApi
  public ResponseEntity<?> handleTicketConfirmation(
    @RequestParam String tripId,
    @RequestParam String status,
    @RequestParam String sellerId,
    @RequestParam String customerId,
    @RequestParam String customerEmail,
    @RequestParam String seatNumber,
    @RequestParam String price,
    @RequestParam String issuedAt
    ) {

    try {
      // --- BẮT ĐẦU LOGIC NGHIỆP VỤ ---
      // 1. Tìm ticket trong DB bằng ticketId
      // 2. Kiểm tra xem ticket đã được xử lý chưa (tránh nhấn 2 lần)
      // 3. Cập nhật trạng thái (Accept hoặc Cancel) vào database

      System.out.println("Đang cập nhật Ticket: " + tripId + " thành trạng thái: " + status);

      // Giả lập xử lý thành công
      boolean isSuccess = true;

      if (isSuccess) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Cập nhật thành công");
        return ResponseEntity.ok(response);
      } else {
        return ResponseEntity.badRequest().body("Không thể cập nhật ticket.");
      }

    } catch (Exception e) {
      return ResponseEntity.status(500).body("Lỗi hệ thống: " + e.getMessage());
    }
  }

}
