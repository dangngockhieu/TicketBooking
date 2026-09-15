package com.ticketbooking.payment.momo;

import com.ticketbooking.payment.config.MomoProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Ký/xác minh chữ ký HMAC-SHA256 cho MoMo Payment Gateway (xem
 * docs/api-design.md §5.1, §5.2). Chuỗi {@code rawSignature} do caller tự ghép
 * đúng thứ tự tham số theo từng API (create/IPN khác nhau), lớp này chỉ chịu
 * trách nhiệm băm bằng secretKey.
 */
@Component
public class MomoSignatureService {

    private final MomoProperties properties;

    public MomoSignatureService(MomoProperties properties) {
        this.properties = properties;
    }

    public String sign(String rawSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawSignature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không thể tính chữ ký HMAC-SHA256 cho MoMo", e);
        }
    }

    /** So khớp không phân biệt hoa/thường — MoMo trả hex signature dạng thường. */
    public boolean matches(String rawSignature, String signature) {
        return sign(rawSignature).equalsIgnoreCase(signature);
    }
}
