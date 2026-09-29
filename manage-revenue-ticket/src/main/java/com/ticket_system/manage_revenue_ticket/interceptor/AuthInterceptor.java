package com.ticket_system.manage_revenue_ticket.interceptor;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.PublicApi;
import com.ticket_system.common.annotation.RoleRequired;
import com.ticket_system.common.exception.AccountLockedException;
import com.ticket_system.common.exception.UnauthorizedRoleException;
import com.ticket_system.common.util.JwtUtil;
import com.ticket_system.manage_revenue_ticket.interceptor.AccountStatusLookup.AccountState;
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
  private final AccountStatusLookup accountStatusLookup;

  public AuthInterceptor(JwtUtil jwtUtil, ObjectMapper mapper, AccountStatusLookup accountStatusLookup) {
    this.jwtUtil = jwtUtil;
    this.mapper = mapper;
    this.accountStatusLookup = accountStatusLookup;
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
    // A token outlives a lock, a role change or a deleted account; the database decides on every request.
    AccountState account = accountStatusLookup.stateOf(userId);
    switch (account.status()) {
      case MISSING -> {
        return writeUnauthorized(response);
      }
      case LOCKED -> {
        return writeError(response, HttpServletResponse.SC_FORBIDDEN, AccountLockedException.MESSAGE);
      }
      case ACTIVE -> { }
    }
    String userRole = account.role() != null ? account.role().name() : jwtUtil.getRoleFromToken(jwtToken);
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
    return writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized - Invalid or missing JWT");
  }

  private boolean writeError(HttpServletResponse response, int status, String message) throws Exception {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(mapper.writeValueAsString(BaseResponseDto.error(status, message)));
    return false;
  }
}
