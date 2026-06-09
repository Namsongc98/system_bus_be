package com.ticket_system.manage_revenue_ticket.interceptor;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.PublicApi;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.common.exception.UnauthorizedRoleException;
import com.ticket_system.manage_revenue_ticket.service.UserService;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.core.annotation.AnnotatedElementUtils;


import java.lang.reflect.Method;
import java.util.Arrays;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

  @Autowired
  private JwtUtil jwtUtil;

  @Autowired
  private ObjectMapper mapper;

  @Autowired
  private UserService userService;

  @Override
  public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler) throws Exception {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }
//        // Kiểm tra annotation @PublicApi
    Method method = handlerMethod.getMethod();
    Method specificMethod =
      AopUtils.getMostSpecificMethod(method, handlerMethod.getBeanType());
//    if (AopUtils.isAopProxy(handlerMethod.getBean())) {
//      method = handlerMethod.getMethod();
//    }
    PublicApi publicApi =
      AnnotatedElementUtils.findMergedAnnotation(
        specificMethod,
        PublicApi.class
      );

    System.out.println(handler.getClass());
    RoleRequired roleRequired = handlerMethod.getMethodAnnotation(RoleRequired.class);
    if (publicApi == null) {
      publicApi = AnnotatedElementUtils.findMergedAnnotation(
        handlerMethod.getBeanType(),
        PublicApi.class
      );
    }
    System.out.println(publicApi);
    if (publicApi != null) {
      return true;
    }
    if (roleRequired == null) {
      throw new UnauthorizedRoleException("Bạn không có quyền truy cập tài nguyên này.");
    }
    // Lấy token từ header
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String jwtToken = authHeader.substring(7);
      System.out.println(jwtUtil.validateToken(jwtToken));
      if (jwtUtil.validateToken(jwtToken)) {
        Long userId = jwtUtil.extractUserId(jwtToken);

        String userRole = jwtUtil.getRoleFromToken(jwtToken);
        UserRole[] allowedRoles = roleRequired.value();
        boolean authorized = Arrays.stream(allowedRoles)
          .anyMatch(role -> role.name().equalsIgnoreCase(userRole));
        if (authorized) {
          request.setAttribute("id", userId);
          request.setAttribute("role", userRole);
          return true;
        } else {
          throw new UnauthorizedRoleException("Bạn không có quyền truy cập tài nguyên này.");
        }
      }
    }
    // Token sai hoặc không có → 401
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.getWriter().write(mapper.writeValueAsString(
      BaseResponseDto.error(HttpServletResponse.SC_UNAUTHORIZED,
        "Unauthorized - Invalid or missing JWT")));
    return false;
  };
}
