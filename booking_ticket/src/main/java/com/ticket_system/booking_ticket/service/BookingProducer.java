package com.ticket_system.booking_ticket.service;

import com.ticket_system.common.Dto.request.TicketRequestDto;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class BookingProducer {
  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;
  public void sendBookingEvent(TicketRequestDto booking) {
    // Gửi thông tin booking sang topic "order-events"
    System.out.println("thông tin booking tại producer: " + booking);
    try {
      CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("order-events", booking.getCustomerEmail(), booking);
      future.whenComplete((result, ex) -> {
        if (ex == null) {
          // ĐÂY LÀ LÚC NHẬN ĐƯỢC ACK THÀNH CÔNG
          System.out.println("Broker đã xác nhận nhận tin nhắn tại offset: "
            + result.getRecordMetadata().offset());
        } else {
          // ĐÂY LÀ LÚC THẤT BẠI (Sau khi đã retry hết mức mà vẫn lỗi)
          System.err.println("Gửi tin nhắn thất bại, lỗi: " + ex.getMessage());
        }
      });
    } catch (Exception e) {
      System.out.println("Lỗi tại message ");
    }
  }
}
