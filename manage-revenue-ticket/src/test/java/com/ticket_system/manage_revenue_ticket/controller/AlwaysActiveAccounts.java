package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.manage_revenue_ticket.interceptor.AccountStatusLookup;
import com.ticket_system.manage_revenue_ticket.interceptor.AccountStatusLookup.AccountState;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * For @WebMvcTest slices: they pick up AuthInterceptor (a HandlerInterceptor bean) but not the
 * repository behind the real AccountStatusLookup. Every account counts as active here; the lock
 * itself is covered by AuthInterceptorTest and UserControllerAuthTest.
 */
@TestConfiguration
public class AlwaysActiveAccounts {

    @Bean
    AccountStatusLookup accountStatusLookup() {
        return userId -> AccountState.active(null);
    }
}
