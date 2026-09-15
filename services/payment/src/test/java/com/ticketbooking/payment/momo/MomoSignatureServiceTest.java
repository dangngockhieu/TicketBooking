package com.ticketbooking.payment.momo;

import com.ticketbooking.payment.config.MomoProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MomoSignatureServiceTest {

    private final MomoProperties properties = new MomoProperties(
            "MOMOPARTNER", "access-key", "secret-key",
            "https://test-payment.momo.vn", "https://ticketbooking.vn/payment/result",
            "http://localhost:8080/api/payments/momo/ipn");

    private final MomoSignatureService signatureService = new MomoSignatureService(properties);

    @Test
    void sign_isDeterministic_forSameInput() {
        String raw = "accessKey=access-key&amount=100000&orderId=abc";

        assertEquals(signatureService.sign(raw), signatureService.sign(raw));
    }

    @Test
    void sign_producesDifferentHash_forDifferentInput() {
        String signature1 = signatureService.sign("orderId=abc");
        String signature2 = signatureService.sign("orderId=xyz");

        assertNotEquals(signature1, signature2);
    }

    @Test
    void sign_producesHexEncodedSha256Length() {
        String signature = signatureService.sign("orderId=abc");

        // HMAC-SHA256 = 32 bytes = 64 ký tự hex
        assertEquals(64, signature.length());
        assertTrue(signature.matches("[0-9a-f]+"));
    }

    @Test
    void matches_returnsTrue_forCorrectSignature_caseInsensitive() {
        String raw = "orderId=abc&amount=100000";
        String signature = signatureService.sign(raw);

        assertTrue(signatureService.matches(raw, signature));
        assertTrue(signatureService.matches(raw, signature.toUpperCase()));
    }

    @Test
    void matches_returnsFalse_whenRawSignatureTampered() {
        String signature = signatureService.sign("orderId=abc&amount=100000");

        assertFalse(signatureService.matches("orderId=abc&amount=999999", signature));
    }
}
