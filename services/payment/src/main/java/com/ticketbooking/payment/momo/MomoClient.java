package com.ticketbooking.payment.momo;

import com.ticketbooking.common.exception.BadGatewayException;
import com.ticketbooking.payment.config.MomoProperties;
import com.ticketbooking.payment.momo.dto.MomoCreatePaymentRequest;
import com.ticketbooking.payment.momo.dto.MomoCreatePaymentResponse;
import com.ticketbooking.payment.momo.dto.MomoRefundRequest;
import com.ticketbooking.payment.momo.dto.MomoRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tích hợp MoMo Payment Gateway (xem docs/api-design.md §5.1) — tạo payUrl qua
 * {@code POST /v2/gateway/api/create}. Chữ ký {@code signature} ký bằng
 * HMAC-SHA256 trên chuỗi raw đúng thứ tự tham số MoMo yêu cầu.
 */
@Slf4j
@Component
public class MomoClient {

    private static final String CREATE_PATH = "/v2/gateway/api/create";
    private static final String REFUND_PATH = "/v2/gateway/api/refund";
    private static final String REQUEST_TYPE = "captureWallet";

    private final RestClient restClient;
    private final MomoProperties properties;
    private final MomoSignatureService signatureService;

    public MomoClient(
            @Qualifier("momoRestClientBuilder") RestClient.Builder restClientBuilder,
            MomoProperties properties,
            MomoSignatureService signatureService) {
        this.restClient = restClientBuilder.baseUrl(properties.endpoint()).build();
        this.properties = properties;
        this.signatureService = signatureService;
    }

    public MomoCreatePaymentResponse createPayment(UUID bookingId, BigDecimal amount, String requestId, String returnUrl) {
        String orderId = bookingId.toString();
        String orderInfo = "Thanh toán vé sự kiện - " + orderId;
        String amountStr = amount.toBigInteger().toString();
        String redirectUrl = (returnUrl != null && !returnUrl.isBlank()) ? returnUrl : properties.redirectUrl();
        String extraData = "";

        String rawSignature = "accessKey=" + properties.accessKey()
                + "&amount=" + amountStr
                + "&extraData=" + extraData
                + "&ipnUrl=" + properties.ipnUrl()
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + properties.partnerCode()
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + REQUEST_TYPE;

        MomoCreatePaymentRequest request = new MomoCreatePaymentRequest(
                properties.partnerCode(),
                properties.accessKey(),
                requestId,
                amountStr,
                orderId,
                orderInfo,
                redirectUrl,
                properties.ipnUrl(),
                REQUEST_TYPE,
                extraData,
                "vi",
                signatureService.sign(rawSignature));

        try {
            MomoCreatePaymentResponse response = restClient.post()
                    .uri(CREATE_PATH)
                    .body(request)
                    .retrieve()
                    .body(MomoCreatePaymentResponse.class);
            if (response == null) {
                throw new BadGatewayException("MoMo không trả về phản hồi hợp lệ.");
            }
            return response;
        } catch (RestClientException e) {
            log.error("Lỗi gọi MoMo create payment cho booking {}: {}", bookingId, e.getMessage());
            throw new BadGatewayException("Không thể kết nối tới cổng thanh toán MoMo.");
        }
    }

    /**
     * Hoàn tiền một giao dịch đã thành công (Saga Compensation, xem
     * docs/api-design.md §5.4) — {@code transId} là mã giao dịch gốc MoMo trả
     * về lúc thanh toán thành công (không phải requestId của lần tạo payUrl).
     */
    public MomoRefundResponse refund(UUID bookingId, BigDecimal amount, long transId, String description) {
        String requestId = UUID.randomUUID().toString();
        String orderId = bookingId + "-refund-" + System.currentTimeMillis();
        String amountStr = amount.toBigInteger().toString();

        String rawSignature = "accessKey=" + properties.accessKey()
                + "&amount=" + amountStr
                + "&description=" + description
                + "&orderId=" + orderId
                + "&partnerCode=" + properties.partnerCode()
                + "&requestId=" + requestId
                + "&transId=" + transId;

        MomoRefundRequest request = new MomoRefundRequest(
                properties.partnerCode(),
                properties.accessKey(),
                orderId,
                requestId,
                amountStr,
                transId,
                "vi",
                description,
                signatureService.sign(rawSignature));

        try {
            MomoRefundResponse response = restClient.post()
                    .uri(REFUND_PATH)
                    .body(request)
                    .retrieve()
                    .body(MomoRefundResponse.class);
            if (response == null) {
                throw new BadGatewayException("MoMo không trả về phản hồi hợp lệ.");
            }
            return response;
        } catch (RestClientException e) {
            log.error("Lỗi gọi MoMo refund cho booking {} (transId={}): {}", bookingId, transId, e.getMessage());
            throw new BadGatewayException("Không thể kết nối tới cổng thanh toán MoMo.");
        }
    }
}
