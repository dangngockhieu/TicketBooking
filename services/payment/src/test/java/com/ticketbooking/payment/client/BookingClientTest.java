package com.ticketbooking.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticketbooking.common.dto.ApiResponse;
import com.ticketbooking.common.exception.ForbiddenException;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import com.ticketbooking.payment.client.dto.BookingDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BookingClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void getBooking_returnsDeserializedData_andForwardsBearerToken() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingDto dto = new BookingDto(bookingId, UUID.randomUUID(), "PENDING_PAYMENT",
                new BigDecimal("3000000"), 2, Instant.now().plusSeconds(600));
        String body = objectMapper.writeValueAsString(ApiResponse.success(dto));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://booking-service/api/bookings/" + bookingId))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        BookingClient client = new BookingClient(builder, "booking-service");
        BookingDto result = client.getBooking(bookingId, "Bearer test-token");

        assertEquals("PENDING_PAYMENT", result.status());
        server.verify();
    }

    @Test
    void getBooking_notFound_throwsResourceNotFoundException() {
        UUID bookingId = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://booking-service/api/bookings/" + bookingId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        BookingClient client = new BookingClient(builder, "booking-service");

        assertThrows(ResourceNotFoundException.class, () -> client.getBooking(bookingId, "Bearer test-token"));
    }

    @Test
    void getBooking_forbidden_throwsForbiddenException() {
        UUID bookingId = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://booking-service/api/bookings/" + bookingId))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        BookingClient client = new BookingClient(builder, "booking-service");

        assertThrows(ForbiddenException.class, () -> client.getBooking(bookingId, "Bearer test-token"));
    }
}
