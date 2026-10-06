package com.ticket_system.booking_ticket.service;

import com.ticket_system.common.Dto.request.TicketRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class BookingProducer {
  private static final Logger log = LoggerFactory.getLogger(BookingProducer.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public BookingProducer(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void sendBookingEvent(TicketRequestDto booking) {
    // Gửi thông tin booking sang topic "order-events". Không log cả payload (email khách).
    log.info("Booking event: trip {} seat {} customer {}",
      booking.getTripId(), booking.getSeatNumber(), booking.getCustomerId());
    try {
      CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("order-events", booking.getCustomerEmail(), booking);
      future.whenComplete((result, ex) -> {
        if (ex == null) {
          // ĐÂY LÀ LÚC NHẬN ĐƯỢC ACK THÀNH CÔNG
          log.info("Broker acked booking event at offset {}", result.getRecordMetadata().offset());
        } else {
          // ĐÂY LÀ LÚC THẤT BẠI (Sau khi đã retry hết mức mà vẫn lỗi)
          log.error("Booking event for customer {} not delivered", booking.getCustomerId(), ex);
        }
      });
    } catch (Exception e) {
      log.error("Booking event for customer {} could not be sent", booking.getCustomerId(), e);
    }
  }
}
