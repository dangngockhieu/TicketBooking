package com.ticketbooking.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import com.ticketbooking.payment.client.dto.CatalogEventDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getEvent_returnsDeserializedData() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        CatalogEventDto dto = new CatalogEventDto(eventId, organizerId, "PUBLISHED", null,
                new BigDecimal("0.05"), new BigDecimal("3000"));
        String body = objectMapper.writeValueAsString(ApiResponse.success(dto));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://catalog-service/api/events/" + eventId))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        CatalogClient client = new CatalogClient(builder, "catalog-service");
        CatalogEventDto result = client.getEvent(eventId);

        assertEquals(organizerId, result.organizerId());
        server.verify();
    }

    @Test
    void getEvent_notFound_throwsResourceNotFoundException() {
        UUID eventId = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://catalog-service/api/events/" + eventId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        CatalogClient client = new CatalogClient(builder, "catalog-service");

        assertThrows(ResourceNotFoundException.class, () -> client.getEvent(eventId));
    }
}
