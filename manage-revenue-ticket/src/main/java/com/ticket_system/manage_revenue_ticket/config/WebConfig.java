package com.ticket_system.manage_revenue_ticket.config;

import com.ticket_system.manage_revenue_ticket.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;

  public WebConfig(AuthInterceptor authInterceptor) {
    this.authInterceptor = authInterceptor;
  }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**"); // áp dụng cho tất cả API
    }
}
