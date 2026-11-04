package com.ticket_system.booking_ticket.controller;

import com.ticket_system.booking_ticket.security.BookingAccountGuard;
import com.ticket_system.booking_ticket.service.BookingProducer;
import com.ticket_system.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * booking_ticket shares GlobalExceptionHandler from common-library (4.1a): errors raised by
 * Spring MVC on booking routes use the standard body with `message`, not a ProblemDetail.
 */
class BookingErrorBodyTest {

    private final BookingProducer producer = mock(BookingProducer.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new BookingController(producer, mock(BookingAccountGuard.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void wrongMethodIs405WithStandardBody() throws Exception {
        mockMvc.perform(get("/api/booking"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.message").value("Phương thức không được hỗ trợ"))
                .andExpect(jsonPath("$.title").doesNotExist());

        verifyNoInteractions(producer);
    }

    @Test
    void nonJsonBodyIs415WithStandardBody() throws Exception {
        mockMvc.perform(post("/api/booking").contentType(MediaType.TEXT_PLAIN).content("seat=5"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Định dạng nội dung không được hỗ trợ"))
                .andExpect(jsonPath("$.title").doesNotExist());

        verifyNoInteractions(producer);
    }
}
