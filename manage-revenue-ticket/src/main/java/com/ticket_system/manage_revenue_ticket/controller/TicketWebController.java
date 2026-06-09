package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.annotation.PublicApi;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
@Controller
@RequestMapping("/ticket")
public class TicketWebController {
  @GetMapping("/confirm-page")
  @PublicApi
  public String showConfirmPage(@RequestParam String ticketId,
                                @RequestParam String status,
                                Model model) {
    // Truyền dữ liệu sang trang HTML để JavaScript sử dụng
    model.addAttribute("ticketId", ticketId);
    model.addAttribute("status", status);
    return "emailConfirmTicket/confirm_landing"; // Trả về file confirm_landing.html
  }
}
