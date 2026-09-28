package com.ticket_system.manage_revenue_ticket.controller;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.annotation.PublicApi;
import com.ticket_system.common.annotation.RoleRequired;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every handler on an admin controller must resolve to @RoleRequired(ADMIN),
 * method-first then class-level — the same lookup AuthInterceptor performs.
 */
class RoleRequiredCoverageTest {

    @ParameterizedTest
    @ValueSource(classes = {
            BusController.class,
            RouteController.class,
            TripController.class,
            RevenueController.class,
            // L12: locked because AuthInterceptor lets any valid JWT through when no @RoleRequired is set.
            SalaryController.class,
            BaseSalaryController.class,
            BaseLoyaltyPointsController.class,
            LoyaltyRewardController.class,
            RedisTestController.class
    })
    void everyHandlerRequiresAdmin(Class<?> controller) {
        List<Method> handlers = handlerMethods(controller);
        assertThat(handlers).isNotEmpty();

        for (Method handler : handlers) {
            RoleRequired effective = effectiveRoleRequired(controller, handler);

            assertThat(effective)
                    .as("%s.%s has no @RoleRequired (method or class level)",
                            controller.getSimpleName(), handler.getName())
                    .isNotNull();
            assertThat(effective.value())
                    .as("%s.%s must be ADMIN-only", controller.getSimpleName(), handler.getName())
                    .containsExactly(UserRole.ADMIN);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "addTicket",
            "updateTicket",
            "getTicketSummaryByBusAndTime",
            "exportTicketSummaryToExcel"
    })
    void ticketAdminEndpointsRequireAdmin(String methodName) {
        Method handler = handlerMethods(TicketController.class).stream()
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No handler named " + methodName));

        RoleRequired effective = effectiveRoleRequired(TicketController.class, handler);

        assertThat(effective).isNotNull();
        assertThat(effective.value()).containsExactly(UserRole.ADMIN);
    }

    @Test
    void ticketPublicEndpointsStayPublic() {
        // The email-confirmation links are followed by anonymous recipients, so
        // these must not inherit an ADMIN requirement (task 2.3 replaces them
        // with signed tokens).
        assertThat(handlerMethods(TicketController.class))
                .filteredOn(method -> AnnotatedElementUtils.findMergedAnnotation(method, PublicApi.class) != null)
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("confirmTicket", "handleTicketConfirmation");
    }

    private static List<Method> handlerMethods(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) != null)
                .toList();
    }

    private static RoleRequired effectiveRoleRequired(Class<?> controller, Method handler) {
        RoleRequired onMethod = AnnotatedElementUtils.findMergedAnnotation(handler, RoleRequired.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(controller, RoleRequired.class);
    }
}
