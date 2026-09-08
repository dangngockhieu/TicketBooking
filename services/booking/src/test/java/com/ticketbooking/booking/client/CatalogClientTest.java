package com.ticketbooking.booking.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticketbooking.booking.client.dto.CatalogEventDto;
import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void getEvent_returnsDeserializedData() throws Exception {
        UUID eventId = UUID.randomUUID();
        CatalogEventDto dto = new CatalogEventDto(
                eventId, "PUBLISHED", Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600),
                List.of(new CatalogEventDto.TicketClassDto(UUID.randomUUID(), "VIP", new BigDecimal("1500000"), 200)));
        String body = objectMapper.writeValueAsString(ApiResponse.success(dto));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://catalog-service/api/events/" + eventId))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        CatalogClient client = new CatalogClient(builder, "catalog-service");
        CatalogEventDto result = client.getEvent(eventId);

        assertEquals("PUBLISHED", result.status());
        assertEquals(1, result.ticketClasses().size());
        server.verify();
    }

    @Test
    void getEvent_notFound_throwsResourceNotFoundException() {
        UUID eventId = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://catalog-service/api/events/" + eventId))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        CatalogClient client = new CatalogClient(builder, "catalog-service");

        assertThrows(ResourceNotFoundException.class, () -> client.getEvent(eventId));
    }
}
