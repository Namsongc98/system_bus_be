package com.ticket_system.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_system.common.Dto.response.BaseResponseDto;
import com.ticket_system.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper mapper;

    public JwtAuthFilter(JwtUtil jwtUtil, ObjectMapper mapper) {
        this.jwtUtil = jwtUtil;
        this.mapper = mapper;
    }

    @Override
        protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        Long userId = null;
        String jwtToken = null;
        try {
            // Lấy token từ header Authorization: Bearer xxx
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwtToken = authHeader.substring(7);
                if (jwtUtil.validateToken(jwtToken)) {
                    userId = jwtUtil.extractUserId(jwtToken);
                }
            }
            String path = request.getRequestURI();

            // Ví dụ: bỏ qua auth cho ảnh avatar
            if (path.startsWith("/avatars/") || path.startsWith("/api/auth/") || path.startsWith("/api/redis-test")) {
                filterChain.doFilter(request, response);
                return;
            }else {
                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    String role = jwtUtil.getRoleFromToken(jwtToken);
                    List<GrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
                    );

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
            // Nếu token hợp lệ và chưa có authentication
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(mapper.writeValueAsString(
                        BaseResponseDto.error(HttpServletResponse.SC_UNAUTHORIZED, ex.getMessage())));
            }
        }
    }

}
