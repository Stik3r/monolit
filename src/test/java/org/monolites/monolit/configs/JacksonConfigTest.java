package org.monolites.monolit.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void createsObjectMapperWithJavaTimeSupport() {
        ObjectMapper objectMapper = new JacksonConfig().objectMapper();

        Map<String, Object> parsed = objectMapper.convertValue(
                Map.of("date", LocalDate.of(2026, Month.JUNE, 25)),
                new TypeReference<Map<String, Object>>() {
                }
        );

        assertThat(parsed).containsEntry("date", List.of(2026, Month.JUNE.getValue(), 25));
    }
}
