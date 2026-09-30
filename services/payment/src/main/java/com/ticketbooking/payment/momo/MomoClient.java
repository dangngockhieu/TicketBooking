package com.ticketbooking.payment.momo;

import com.ticketbooking.common.exception.BadGatewayException;
import com.ticketbooking.payment.config.MomoProperties;
import com.ticketbooking.payment.momo.dto.MomoCreatePaymentRequest;
import com.ticketbooking.payment.momo.dto.MomoCreatePaymentResponse;
import com.ticketbooking.payment.momo.dto.MomoDisburseRequest;
import com.ticketbooking.payment.momo.dto.MomoDisburseResponse;
import com.ticketbooking.payment.momo.dto.MomoRefundRequest;
import com.ticketbooking.payment.momo.dto.MomoRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
    private static final String DISBURSE_PATH = "/v2/gateway/api/disburse";
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

    @Retryable(
            retryFor = {
                    ResourceAccessException.class,
                    HttpServerErrorException.ServiceUnavailable.class,
                    HttpServerErrorException.GatewayTimeout.class,
                    HttpClientErrorException.TooManyRequests.class
            },
            maxAttempts = 2,
            backoff = @Backoff(delay = 500))
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
        } catch (ResourceAccessException | HttpServerErrorException.ServiceUnavailable
                | HttpServerErrorException.GatewayTimeout | HttpClientErrorException.TooManyRequests e) {
            log.warn("Lỗi thoáng qua gọi MoMo create payment cho booking {}: {}", bookingId, e.getMessage());
            throw e;
        } catch (RestClientException e) {
            log.error("Lỗi gọi MoMo create payment cho booking {}: {}", bookingId, e.getMessage());
            throw new BadGatewayException("Không thể kết nối tới cổng thanh toán MoMo.");
        }
    }

    /**
     * Hoàn tiền một giao dịch đã thành công (Saga Compensation, xem
     * docs/api-design.md §5.4) — {@code transId} là mã giao dịch gốc MoMo trả
     * về lúc thanh toán thành công (không phải requestId của lần tạo payUrl).
     * {@code requestId} do caller truyền vào (ổn định theo giao dịch, xem
     * PaymentServiceImpl) thay vì tự sinh ngẫu nhiên — bắt buộc để retry (hoặc
     * gọi lại thủ công sau này) tái sử dụng đúng cùng một requestId, giữ tính
     * idempotent phía MoMo.
     */
    @Retryable(
            retryFor = {
                    ResourceAccessException.class,
                    HttpServerErrorException.ServiceUnavailable.class,
                    HttpServerErrorException.GatewayTimeout.class,
                    HttpClientErrorException.TooManyRequests.class
            },
            maxAttempts = 2,
            backoff = @Backoff(delay = 500))
    public MomoRefundResponse refund(UUID bookingId, BigDecimal amount, long transId, String description, String requestId) {
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
        } catch (ResourceAccessException | HttpServerErrorException.ServiceUnavailable
                | HttpServerErrorException.GatewayTimeout | HttpClientErrorException.TooManyRequests e) {
            log.warn("Lỗi thoáng qua gọi MoMo refund cho booking {} (transId={}): {}", bookingId, transId, e.getMessage());
            throw e;
        } catch (RestClientException e) {
            log.error("Lỗi gọi MoMo refund cho booking {} (transId={}): {}", bookingId, transId, e.getMessage());
            throw new BadGatewayException("Không thể kết nối tới cổng thanh toán MoMo.");
        }
    }

    /**
     * Chi trả cho Organizer qua MoMo Business Disbursement API (xem
     * docs/api-design.md §7.5) — {@code receiver} là số tài khoản ngân hàng
     * hoặc số điện thoại ví MoMo người nhận. {@code requestId} do caller truyền
     * vào (ổn định theo payout, xem PayoutServiceImpl) thay vì tự sinh ngẫu
     * nhiên — cùng lý do như {@link #refund}.
     */
    @Retryable(
            retryFor = {
                    ResourceAccessException.class,
                    HttpServerErrorException.ServiceUnavailable.class,
                    HttpServerErrorException.GatewayTimeout.class,
                    HttpClientErrorException.TooManyRequests.class
            },
            maxAttempts = 2,
            backoff = @Backoff(delay = 500))
    public MomoDisburseResponse disburse(UUID payoutRequestId, BigDecimal amount, String receiver, String description, String requestId) {
        String orderId = payoutRequestId.toString();
        String amountStr = amount.toBigInteger().toString();

        String rawSignature = "accessKey=" + properties.accessKey()
                + "&amount=" + amountStr
                + "&description=" + description
                + "&orderId=" + orderId
                + "&partnerCode=" + properties.partnerCode()
                + "&receiver=" + receiver
                + "&requestId=" + requestId;

        MomoDisburseRequest request = new MomoDisburseRequest(
                properties.partnerCode(),
                properties.accessKey(),
                requestId,
                orderId,
                amountStr,
                receiver,
                description,
                signatureService.sign(rawSignature));

        try {
            MomoDisburseResponse response = restClient.post()
                    .uri(DISBURSE_PATH)
                    .body(request)
                    .retrieve()
                    .body(MomoDisburseResponse.class);
            if (response == null) {
                throw new BadGatewayException("MoMo không trả về phản hồi hợp lệ.");
            }
            return response;
        } catch (ResourceAccessException | HttpServerErrorException.ServiceUnavailable
                | HttpServerErrorException.GatewayTimeout | HttpClientErrorException.TooManyRequests e) {
            log.warn("Lỗi thoáng qua gọi MoMo disburse cho payout {}: {}", payoutRequestId, e.getMessage());
            throw e;
        } catch (RestClientException e) {
            log.error("Lỗi gọi MoMo disburse cho payout {}: {}", payoutRequestId, e.getMessage());
            throw new BadGatewayException("Không thể kết nối tới cổng thanh toán MoMo.");
        }
    }

    /**
     * Sau khi retry cạn (xem @Retryable trên createPayment) mà vẫn lỗi thoáng
     * qua — chuẩn hóa về BadGatewayException (502) như mọi lỗi MoMo khác, thay
     * vì để exception thô lọt ra ngoài thành 500 chung chung.
     */
    @Recover
    public MomoCreatePaymentResponse recoverCreatePayment(
            Exception e, UUID bookingId, BigDecimal amount, String requestId, String returnUrl) {
        log.error("MoMo create payment cho booking {} vẫn lỗi sau khi retry: {}", bookingId, e.getMessage());
        throw new BadGatewayException("Không thể kết nối tới cổng thanh toán MoMo.");
    }

    @Recover
    public MomoRefundResponse recoverRefund(
            Exception e, UUID bookingId, BigDecimal amount, long transId, String description, String requestId) {
        log.error("MoMo refund cho booking {} (transId={}) vẫn lỗi sau khi retry: {}", bookingId, transId, e.getMessage());
        throw new BadGatewayException("Không thể kết nối tới cổng thanh toán MoMo.");
    }

    @Recover
    public MomoDisburseResponse recoverDisburse(
            Exception e, UUID payoutRequestId, BigDecimal amount, String receiver, String description, String requestId) {
        log.error("MoMo disburse cho payout {} vẫn lỗi sau khi retry: {}", payoutRequestId, e.getMessage());
        throw new BadGatewayException("Không thể kết nối tới cổng thanh toán MoMo.");
    }
}
