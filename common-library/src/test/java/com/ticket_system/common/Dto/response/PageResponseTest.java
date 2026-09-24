package com.ticket_system.common.Dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void copiesContentAndNumbersFromPage() {
        Page<String> page = new PageImpl<>(List.of("k", "l"), PageRequest.of(2, 5), 12);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("k", "l");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(12);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void appliesMapperToEachElement() {
        Page<Integer> page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(0, 3), 3);

        PageResponse<String> response = PageResponse.from(page, n -> "#" + n);

        assertThat(response.content()).containsExactly("#1", "#2", "#3");
        assertThat(response.totalElements()).isEqualTo(3);
    }

    @Test
    void emptyPageHasEmptyContentAndNoPages() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).isNotNull().isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void nullContentBecomesEmptyList() {
        PageResponse<String> response = new PageResponse<>(null, 0, 10, 0, 0);

        assertThat(response.content()).isNotNull().isEmpty();
    }

    @Test
    void unpagedPageDoesNotThrow() {
        Page<String> page = new PageImpl<>(List.of("a", "b"));

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void serializesToExactlyTheFiveContractKeys() throws Exception {
        Page<String> page = new PageImpl<>(List.of("a"), PageRequest.of(1, 1), 3);

        JsonNode json = new ObjectMapper().valueToTree(PageResponse.from(page));

        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");
        assertThat(json.get("page").asInt()).isEqualTo(1);
        assertThat(json.get("totalElements").asLong()).isEqualTo(3);
    }
}
