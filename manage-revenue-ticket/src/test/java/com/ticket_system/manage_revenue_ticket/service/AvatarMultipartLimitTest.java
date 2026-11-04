package com.ticket_system.manage_revenue_ticket.service;

import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 2MB avatar limit is enforced by the multipart resolver (413) before AvatarService sees the
 * file; MockMvc does not apply it. Feed the real application.properties to Spring Boot's multipart
 * auto-configuration and check the limits it actually hands to the servlet container.
 */
class AvatarMultipartLimitTest {

    @Test
    void springAppliesTheAvatarLimitToTheServletContainer() throws Exception {
        Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MultipartAutoConfiguration.class))
                .withPropertyValues(
                        "spring.servlet.multipart.max-file-size=" + properties.getProperty("spring.servlet.multipart.max-file-size"),
                        "spring.servlet.multipart.max-request-size=" + properties.getProperty("spring.servlet.multipart.max-request-size"))
                .run(context -> {
                    MultipartConfigElement multipart = context.getBean(MultipartConfigElement.class);

                    assertThat(multipart.getMaxFileSize()).isEqualTo(AvatarService.MAX_BYTES);
                    // room for the multipart envelope, but not an open door
                    assertThat(multipart.getMaxRequestSize())
                            .isGreaterThan(multipart.getMaxFileSize())
                            .isLessThanOrEqualTo(2 * multipart.getMaxFileSize());
                });
    }
}
