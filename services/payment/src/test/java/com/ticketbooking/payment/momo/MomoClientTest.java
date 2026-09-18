package com.ticketbooking.payment.momo;

import com.ticketbooking.common.exception.BadGatewayException;
import com.ticketbooking.payment.config.MomoProperties;
import com.ticketbooking.payment.momo.dto.MomoCreatePaymentResponse;
import com.ticketbooking.payment.momo.dto.MomoRefundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MomoClientTest {

    private final MomoProperties properties = new MomoProperties(
            "MOMOPARTNER", "access-key", "secret-key",
            "https://test-payment.momo.vn", "https://ticketbooking.vn/payment/result",
            "http://localhost:8080/api/payments/momo/ipn");

    private MockRestServiceServer server;
    private MomoClient momoClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        momoClient = new MomoClient(builder, properties, new MomoSignatureService(properties));
    }

    @Test
    void createPayment_returnsPayUrl_onSuccess() {
        UUID bookingId = UUID.randomUUID();
        String body = """
                {"partnerCode":"MOMOPARTNER","orderId":"%s","requestId":"req-1","amount":3000000,
                 "resultCode":0,"message":"Successful.","payUrl":"https://payment.momo.vn/pay?t=abc"}
                """.formatted(bookingId);

        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/create"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.partnerCode").value("MOMOPARTNER"))
                .andExpect(jsonPath("$.accessKey").value("access-key"))
                .andExpect(jsonPath("$.orderId").value(bookingId.toString()))
                .andExpect(jsonPath("$.amount").value("3000000"))
                .andExpect(jsonPath("$.requestType").value("captureWallet"))
                .andExpect(jsonPath("$.signature").exists())
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        MomoCreatePaymentResponse response = momoClient.createPayment(
                bookingId, new BigDecimal("3000000"), "req-1", null);

        assertEquals("https://payment.momo.vn/pay?t=abc", response.payUrl());
        assertEquals(true, response.isSuccess());
        server.verify();
    }

    @Test
    void createPayment_usesConfiguredRedirectUrl_whenReturnUrlNotProvided() {
        UUID bookingId = UUID.randomUUID();
        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/create"))
                .andExpect(jsonPath("$.redirectUrl").value("https://ticketbooking.vn/payment/result"))
                .andRespond(withSuccess("{\"resultCode\":0}", MediaType.APPLICATION_JSON));

        momoClient.createPayment(bookingId, new BigDecimal("100000"), "req-2", null);

        server.verify();
    }

    @Test
    void createPayment_throwsBadGateway_onServerError() {
        UUID bookingId = UUID.randomUUID();
        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/create"))
                .andRespond(withServerError());

        assertThrows(BadGatewayException.class,
                () -> momoClient.createPayment(bookingId, new BigDecimal("100000"), "req-3", null));
    }

    @Test
    void refund_returnsSuccess_andSignsCorrectFields() {
        UUID bookingId = UUID.randomUUID();
        String body = """
                {"partnerCode":"MOMOPARTNER","requestId":"req-1","amount":3000000,
                 "resultCode":0,"message":"Successful.","transId":9999999}
                """;

        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/refund"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.partnerCode").value("MOMOPARTNER"))
                .andExpect(jsonPath("$.accessKey").value("access-key"))
                .andExpect(jsonPath("$.amount").value("3000000"))
                .andExpect(jsonPath("$.transId").value(4123456789L))
                .andExpect(jsonPath("$.description").value("Hoàn tiền do lỗi hệ thống"))
                .andExpect(jsonPath("$.signature").exists())
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        MomoRefundResponse response = momoClient.refund(
                bookingId, new BigDecimal("3000000"), 4123456789L, "Hoàn tiền do lỗi hệ thống");

        assertEquals(true, response.isSuccess());
        server.verify();
    }

    @Test
    void refund_throwsBadGateway_onServerError() {
        UUID bookingId = UUID.randomUUID();
        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/refund"))
                .andRespond(withServerError());

        assertThrows(BadGatewayException.class,
                () -> momoClient.refund(bookingId, new BigDecimal("100000"), 123L, "test"));
    }
}
