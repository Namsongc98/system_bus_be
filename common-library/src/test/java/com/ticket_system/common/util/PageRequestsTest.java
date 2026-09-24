package com.ticket_system.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestsTest {

    @Test
    void buildsPageableForValidValues() {
        Pageable pageable = PageRequests.of(3, 20);

        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    void acceptsMaxSize() {
        assertThat(PageRequests.of(0, PageRequests.MAX_SIZE).getPageSize()).isEqualTo(100);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> PageRequests.of(-1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroSize() {
        assertThatThrownBy(() -> PageRequests.of(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSizeAboveMax() {
        assertThatThrownBy(() -> PageRequests.of(0, PageRequests.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }
}
