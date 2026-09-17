package com.ticketbooking.payment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.event.PaymentFailedEvent;
import com.ticketbooking.common.event.PaymentSuccessEvent;
import com.ticketbooking.common.exception.BadGatewayException;
import com.ticketbooking.common.exception.BadRequestException;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.payment.client.BookingClient;
import com.ticketbooking.payment.client.dto.BookingDto;
import com.ticketbooking.payment.config.MomoProperties;
import com.ticketbooking.payment.dto.request.InitiatePaymentRequest;
import com.ticketbooking.payment.dto.response.PaymentInitiateResponse;
import com.ticketbooking.payment.entity.Transaction;
import com.ticketbooking.payment.enums.TransactionStatus;
import com.ticketbooking.payment.event.PaymentEventPublisher;
import com.ticketbooking.payment.momo.MomoClient;
import com.ticketbooking.payment.momo.MomoSignatureService;
import com.ticketbooking.payment.momo.dto.MomoCreatePaymentResponse;
import com.ticketbooking.payment.momo.dto.MomoIpnRequest;
import com.ticketbooking.payment.repository.TransactionRepository;
import com.ticketbooking.payment.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String STATUS_PAID = "PAID";
    private static final List<TransactionStatus> ACTIVE_TRANSACTION_STATUSES =
            List.of(TransactionStatus.PENDING, TransactionStatus.SUCCESS);

    private final TransactionRepository transactionRepository;
    private final BookingClient bookingClient;
    private final MomoClient momoClient;
    private final MomoSignatureService momoSignatureService;
    private final MomoProperties momoProperties;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PaymentServiceImpl(
            TransactionRepository transactionRepository,
            BookingClient bookingClient,
            MomoClient momoClient,
            MomoSignatureService momoSignatureService,
            MomoProperties momoProperties,
            PaymentEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.bookingClient = bookingClient;
        this.momoClient = momoClient;
        this.momoSignatureService = momoSignatureService;
        this.momoProperties = momoProperties;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentInitiateResponse initiate(UUID customerId, String bearerToken, InitiatePaymentRequest request) {
        log.info("Customer {} khởi tạo thanh toán cho booking {}", customerId, request.bookingId());
        BookingDto booking = bookingClient.getBooking(request.bookingId(), bearerToken);

        if (STATUS_PAID.equals(booking.status())) {
            throw new ConflictException("Booking đã thanh toán trước đó.");
        }
        if (!STATUS_PENDING_PAYMENT.equals(booking.status())) {
            throw new ConflictException("Booking không ở trạng thái chờ thanh toán.");
        }
        if (booking.expiredAt() != null && booking.expiredAt().isBefore(Instant.now())) {
            throw new ConflictException("Booking đã hết hạn giữ chỗ, không thể thanh toán.");
        }
        if (transactionRepository.existsByBookingIdAndStatusIn(booking.id(), ACTIVE_TRANSACTION_STATUSES)) {
            throw new ConflictException("Đã có giao dịch thanh toán cho booking này.");
        }

        Transaction transaction = Transaction.builder()
                .bookingId(booking.id())
                .amount(booking.totalAmount())
                .paymentMethod("MOMO")
                .status(TransactionStatus.PENDING)
                .build();
        transaction = transactionRepository.save(transaction);

        MomoCreatePaymentResponse momoResponse = momoClient.createPayment(
                booking.id(), booking.totalAmount(), transaction.getId().toString(), request.returnUrl());

        transaction.setGatewayResponse(toGatewayResponseMap(momoResponse));
        if (!momoResponse.isSuccess()) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            log.warn("MoMo từ chối tạo payUrl cho booking {}: resultCode={}, message={}",
                    booking.id(), momoResponse.resultCode(), momoResponse.message());
            throw new BadGatewayException("MoMo từ chối khởi tạo thanh toán: " + momoResponse.message());
        }
        transactionRepository.save(transaction);

        return new PaymentInitiateResponse(
                transaction.getId(), booking.id(), booking.totalAmount(), momoResponse.payUrl(), booking.expiredAt());
    }

    @Override
    public void handleMomoIpn(MomoIpnRequest request) {
        String rawSignature = buildIpnRawSignature(request);
        if (!momoSignatureService.matches(rawSignature, request.signature())) {
            throw new BadRequestException("Chữ ký MoMo không hợp lệ.");
        }

        String transId = String.valueOf(request.transId());
        if (transactionRepository.findByGatewayTransId(transId).isPresent()) {
            log.info("MoMo IPN transId {} đã được xử lý trước đó, bỏ qua (idempotent).", transId);
            return;
        }

        UUID transactionId;
        try {
            transactionId = UUID.fromString(request.requestId());
        } catch (IllegalArgumentException e) {
            log.warn("MoMo IPN requestId không hợp lệ, bỏ qua: {}", request.requestId());
            return;
        }

        Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
        if (transaction == null) {
            log.warn("Không tìm thấy giao dịch cho MoMo IPN requestId {}, bỏ qua.", request.requestId());
            return;
        }
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            log.info("Giao dịch {} không còn ở trạng thái PENDING (hiện tại: {}), bỏ qua IPN.",
                    transaction.getId(), transaction.getStatus());
            return;
        }
        if (request.amount() == null || transaction.getAmount().compareTo(BigDecimal.valueOf(request.amount())) != 0) {
            throw new BadRequestException("Số tiền trong IPN không khớp với giao dịch.");
        }

        transaction.setGatewayTransId(transId);

        if (request.resultCode() != null && request.resultCode() == 0) {
            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setPaidAt(Instant.now());
            transactionRepository.save(transaction);

            eventPublisher.publishSuccess(PaymentSuccessEvent.builder()
                    .eventType("payment.success")
                    .transactionId(transaction.getId())
                    .bookingId(transaction.getBookingId())
                    .amount(transaction.getAmount())
                    .gatewayTransId(transId)
                    .paidAt(transaction.getPaidAt())
                    .build());
        } else {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);

            eventPublisher.publishFailed(PaymentFailedEvent.builder()
                    .eventType("payment.failed")
                    .transactionId(transaction.getId())
                    .bookingId(transaction.getBookingId())
                    .reason(request.message())
                    .build());
        }
    }

    private String buildIpnRawSignature(MomoIpnRequest request) {
        return "accessKey=" + momoProperties.accessKey()
                + "&amount=" + request.amount()
                + "&extraData=" + emptyIfNull(request.extraData())
                + "&message=" + request.message()
                + "&orderId=" + request.orderId()
                + "&orderInfo=" + request.orderInfo()
                + "&orderType=" + request.orderType()
                + "&partnerCode=" + request.partnerCode()
                + "&payType=" + request.payType()
                + "&requestId=" + request.requestId()
                + "&responseTime=" + request.responseTime()
                + "&resultCode=" + request.resultCode()
                + "&transId=" + request.transId();
    }

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }

    private Map<String, Object> toGatewayResponseMap(MomoCreatePaymentResponse response) {
        return objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {
        });
    }
}
