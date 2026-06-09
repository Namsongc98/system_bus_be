package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.request.TicketRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import org.springframework.web.util.UriComponentsBuilder;


@Service
@RequiredArgsConstructor
public class EmailConfirmTicketConsumer {

  @Autowired
  private EmailService emailService;

  @KafkaListener(topics = "order-events", groupId = "email-service-group")
  public void emailConsume(TicketRequestDto ticket, Acknowledgment ack) {

    String acceptUrl = UriComponentsBuilder.fromHttpUrl("http://localhost:8082/ticket/confirm-page")
      .queryParam("tripId", ticket.getTripId())
      .queryParam("status", "accept") // Hoặc dto.getStatus()
      .queryParam("sellerId", ticket.getSellerId())
      .queryParam("customerId", ticket.getCustomerId())
      .queryParam("customerEmail", ticket.getCustomerEmail())
      .queryParam("seatNumber", ticket.getSeatNumber())
      .queryParam("price", ticket.getPrice())
      .queryParam("issuedAt")
      .toUriString();


    String cancelUrl = UriComponentsBuilder.fromHttpUrl("http://localhost:8082/ticket/confirm-page")
      .queryParam("tripId", ticket.getTripId())
      .queryParam("status", "cancel") // Hoặc dto.getStatus()
      .queryParam("sellerId", ticket.getSellerId())
      .queryParam("customerId", ticket.getCustomerId())
      .queryParam("customerEmail", ticket.getCustomerEmail())
      .queryParam("seatNumber", ticket.getSeatNumber())
      .queryParam("price", ticket.getPrice())
      .queryParam("issuedAt")
      .toUriString();

    System.out.println(
      "emailConsume instance: " +
        Thread.currentThread().getName() +
        " => " + ticket
    );

    try {
      // 1. Xử lý logic nghiệp vụ
      this.emailService.sendTicketConfirmEmail(
        ticket.getCustomerEmail(),
        acceptUrl,
        cancelUrl
      );
      // 2. CHỈ COMMIT KHI XỬ LÝ XONG
      // Nếu dòng này không được gọi (do crash), Kafka sẽ gửi lại message này khi consumer khởi động lại
      ack.acknowledge();
    } catch (Exception e) {
      // Log lỗi, có thể đẩy vào Topic Dead Letter Queue (DLQ) để xử lý sau
      System.err.println("Gửi mail thất bại cho " + ticket.getCustomerEmail() + ": " + e.getMessage());
      // Tại đây có thể code logic đẩy vào "Dead Letter Topic" để xử lý lại sau
    }
  }

}
