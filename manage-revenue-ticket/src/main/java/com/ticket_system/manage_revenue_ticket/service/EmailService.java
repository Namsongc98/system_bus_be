package com.ticket_system.manage_revenue_ticket.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class EmailService {

  private final JavaMailSender mailSender;
  private final SpringTemplateEngine templateEngine;

  public void sendTicketConfirmEmail(
    String email,
    String acceptUrl,
    String cancelUrl) throws Exception {

    Context context = new Context();
    context.setVariable("acceptUrl", acceptUrl);
    context.setVariable("cancelUrl", cancelUrl);

    String html = templateEngine.process(
      "emailConfirmTicket/ticket-confirm",
      context
    );
    System.out.println(html);
    MimeMessage message = mailSender.createMimeMessage();

    MimeMessageHelper helper =
      new MimeMessageHelper(message, true);

    helper.setTo(email);
    helper.setSubject("Xác nhận đặt vé");
    helper.setText(html, true);

    mailSender.send(message);
  }
}
