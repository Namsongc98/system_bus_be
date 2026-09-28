package com.ticket_system.manage_revenue_ticket.interceptor;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.PublicApi;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.common.exception.UnauthorizedRoleException;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

@Component
public class AuthInterceptor implements HandlerInterceptor {
  private static final String ACCESS_DENIED_MESSAGE = "Bạn không có quyền truy cập tài nguyên này.";

  private final JwtUtil jwtUtil;
  private final ObjectMapper mapper;

  public AuthInterceptor(JwtUtil jwtUtil, ObjectMapper mapper) {
    this.jwtUtil = jwtUtil;
    this.mapper = mapper;
  }

  @Override
  public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler) throws Exception {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }

    Method method = handlerMethod.getMethod();
    Method specificMethod = AopUtils.getMostSpecificMethod(method, handlerMethod.getBeanType());

    if (findAnnotation(specificMethod, handlerMethod, PublicApi.class) != null) {
      return true;
    }

    RoleRequired roleRequired = findAnnotation(
      specificMethod,
      handlerMethod,
      RoleRequired.class
    );
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return writeUnauthorized(response);
    }

    String jwtToken = authHeader.substring(7);
    if (!jwtUtil.validateToken(jwtToken)) {
      return writeUnauthorized(response);
    }

    Long userId = jwtUtil.extractUserId(jwtToken);
    String userRole = jwtUtil.getRoleFromToken(jwtToken);
    request.setAttribute("id", userId);
    request.setAttribute("role", userRole);

    if (roleRequired == null) {
      return true;
    }

    boolean authorized = userRole != null && Arrays.stream(roleRequired.value())
      .map(UserRole::name)
      .anyMatch(role -> role.equalsIgnoreCase(userRole));

    if (!authorized) {
      throw new UnauthorizedRoleException(ACCESS_DENIED_MESSAGE);
    }
    return true;
  }

  private <A extends java.lang.annotation.Annotation> A findAnnotation(
    Method specificMethod,
    HandlerMethod handlerMethod,
    Class<A> annotationType
  ) {
    A annotation = AnnotatedElementUtils.findMergedAnnotation(specificMethod, annotationType);
    if (annotation != null) {
      return annotation;
    }
    return AnnotatedElementUtils.findMergedAnnotation(
      handlerMethod.getBeanType(),
      annotationType
    );
  }

  private boolean writeUnauthorized(HttpServletResponse response) throws Exception {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(mapper.writeValueAsString(
      BaseResponseDto.error(HttpServletResponse.SC_UNAUTHORIZED,
        "Unauthorized - Invalid or missing JWT")));
    return false;
  }
}
