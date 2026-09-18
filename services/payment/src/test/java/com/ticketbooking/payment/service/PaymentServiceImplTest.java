package com.ticketbooking.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.exception.BadGatewayException;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.payment.client.BookingClient;
import com.ticketbooking.payment.client.dto.BookingDto;
import com.ticketbooking.payment.config.MomoProperties;
import com.ticketbooking.payment.dto.request.InitiatePaymentRequest;
import com.ticketbooking.payment.dto.response.PaymentInitiateResponse;
import com.ticketbooking.payment.entity.Transaction;
import com.ticketbooking.payment.enums.TransactionStatus;
import com.ticketbooking.payment.event.PaymentEventPublisher;
import com.ticketbooking.common.event.BookingRefundRequestedEvent;
import com.ticketbooking.common.event.PaymentFailedEvent;
import com.ticketbooking.common.event.PaymentRefundedEvent;
import com.ticketbooking.common.event.PaymentSuccessEvent;
import com.ticketbooking.common.exception.BadRequestException;
import com.ticketbooking.payment.momo.MomoClient;
import com.ticketbooking.payment.momo.MomoSignatureService;
import com.ticketbooking.payment.momo.dto.MomoCreatePaymentResponse;
import com.ticketbooking.payment.momo.dto.MomoIpnRequest;
import com.ticketbooking.payment.momo.dto.MomoRefundResponse;
import com.ticketbooking.payment.repository.TransactionRepository;
import com.ticketbooking.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BookingClient bookingClient;

    @Mock
    private MomoClient momoClient;

    @Mock
    private PaymentEventPublisher eventPublisher;

    private MomoProperties momoProperties;
    private MomoSignatureService momoSignatureService;
    private PaymentServiceImpl paymentService;

    private UUID customerId;
    private UUID bookingId;
    private static final String TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        momoProperties = new MomoProperties("MOMOPARTNER", "access-key", "secret-key",
                "https://test-payment.momo.vn", "https://ticketbooking.vn/payment/result",
                "http://localhost:8080/api/payments/momo/ipn");
        momoSignatureService = new MomoSignatureService(momoProperties);
        paymentService = new PaymentServiceImpl(transactionRepository, bookingClient, momoClient,
                momoSignatureService, momoProperties, eventPublisher, new ObjectMapper());
        customerId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
    }

    private BookingDto pendingBooking() {
        return new BookingDto(bookingId, UUID.randomUUID(), "PENDING_PAYMENT",
                new BigDecimal("3000000"), Instant.now().plusSeconds(600));
    }

    @Test
    void initiate_succeeds_andReturnsPayUrl() {
        when(bookingClient.getBooking(bookingId, TOKEN)).thenReturn(pendingBooking());
        when(transactionRepository.existsByBookingIdAndStatusIn(eq(bookingId), any())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        MomoCreatePaymentResponse momoResponse = new MomoCreatePaymentResponse(
                "MOMOPARTNER", bookingId.toString(), "req-1", 3000000L, 0, "Successful.",
                "https://payment.momo.vn/pay?t=abc", null, null);
        when(momoClient.createPayment(eq(bookingId), any(), any(), any())).thenReturn(momoResponse);

        PaymentInitiateResponse response = paymentService.initiate(
                customerId, TOKEN, new InitiatePaymentRequest(bookingId, "https://ticketbooking.vn/result"));

        assertEquals("https://payment.momo.vn/pay?t=abc", response.paymentUrl());
        assertEquals(bookingId, response.bookingId());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        assertEquals(TransactionStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    void initiate_throwsConflict_whenBookingAlreadyPaid() {
        BookingDto paidBooking = new BookingDto(bookingId, UUID.randomUUID(), "PAID",
                new BigDecimal("3000000"), Instant.now().plusSeconds(600));
        when(bookingClient.getBooking(bookingId, TOKEN)).thenReturn(paidBooking);

        assertThrows(ConflictException.class, () -> paymentService.initiate(
                customerId, TOKEN, new InitiatePaymentRequest(bookingId, null)));
        verifyNoInteractions(momoClient);
    }

    @Test
    void initiate_throwsConflict_whenBookingExpired() {
        BookingDto expiredBooking = new BookingDto(bookingId, UUID.randomUUID(), "PENDING_PAYMENT",
                new BigDecimal("3000000"), Instant.now().minusSeconds(60));
        when(bookingClient.getBooking(bookingId, TOKEN)).thenReturn(expiredBooking);

        assertThrows(ConflictException.class, () -> paymentService.initiate(
                customerId, TOKEN, new InitiatePaymentRequest(bookingId, null)));
        verifyNoInteractions(momoClient);
    }

    @Test
    void initiate_throwsConflict_whenActiveTransactionAlreadyExists() {
        when(bookingClient.getBooking(bookingId, TOKEN)).thenReturn(pendingBooking());
        when(transactionRepository.existsByBookingIdAndStatusIn(eq(bookingId),
                eq(List.of(TransactionStatus.PENDING, TransactionStatus.SUCCESS)))).thenReturn(true);

        assertThrows(ConflictException.class, () -> paymentService.initiate(
                customerId, TOKEN, new InitiatePaymentRequest(bookingId, null)));
        verifyNoInteractions(momoClient);
    }

    @Test
    void initiate_throwsBadGateway_andMarksFailed_whenMomoRejects() {
        when(bookingClient.getBooking(bookingId, TOKEN)).thenReturn(pendingBooking());
        when(transactionRepository.existsByBookingIdAndStatusIn(eq(bookingId), any())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        MomoCreatePaymentResponse rejected = new MomoCreatePaymentResponse(
                "MOMOPARTNER", bookingId.toString(), "req-1", 3000000L, 99, "Invalid signature.",
                null, null, null);
        when(momoClient.createPayment(eq(bookingId), any(), any(), any())).thenReturn(rejected);

        assertThrows(BadGatewayException.class, () -> paymentService.initiate(
                customerId, TOKEN, new InitiatePaymentRequest(bookingId, null)));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        assertEquals(TransactionStatus.FAILED, captor.getValue().getStatus());
    }

    private MomoIpnRequest ipnRequest(UUID transactionId, long amount, int resultCode, String message) {
        String orderId = bookingId.toString();
        String requestId = transactionId.toString();
        String orderInfo = "Thanh toán vé sự kiện - " + orderId;
        String orderType = "momo_wallet";
        long transId = 4123456789L;
        String payType = "qr";
        long responseTime = 1718448000000L;
        String extraData = "";

        String rawSignature = "accessKey=" + momoProperties.accessKey()
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&message=" + message
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&orderType=" + orderType
                + "&partnerCode=" + momoProperties.partnerCode()
                + "&payType=" + payType
                + "&requestId=" + requestId
                + "&responseTime=" + responseTime
                + "&resultCode=" + resultCode
                + "&transId=" + transId;
        String signature = momoSignatureService.sign(rawSignature);

        return new MomoIpnRequest(momoProperties.partnerCode(), orderId, requestId, amount, orderInfo, orderType,
                transId, resultCode, message, payType, responseTime, extraData, signature);
    }

    @Test
    void handleMomoIpn_success_updatesTransactionAndPublishesSuccessEvent() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(transactionId).bookingId(bookingId).amount(new BigDecimal("3000000"))
                .paymentMethod("MOMO").status(TransactionStatus.PENDING).build();
        MomoIpnRequest request = ipnRequest(transactionId, 3000000L, 0, "Successful.");

        when(transactionRepository.findByGatewayTransId("4123456789")).thenReturn(Optional.empty());
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        paymentService.handleMomoIpn(request);

        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals("4123456789", transaction.getGatewayTransId());
        assertNotNull(transaction.getPaidAt());
        verify(transactionRepository).save(transaction);

        ArgumentCaptor<PaymentSuccessEvent> captor = ArgumentCaptor.forClass(PaymentSuccessEvent.class);
        verify(eventPublisher).publishSuccess(captor.capture());
        assertEquals(bookingId, captor.getValue().getBookingId());
    }

    @Test
    void handleMomoIpn_failedResultCode_marksFailedAndPublishesFailedEvent() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(transactionId).bookingId(bookingId).amount(new BigDecimal("3000000"))
                .paymentMethod("MOMO").status(TransactionStatus.PENDING).build();
        MomoIpnRequest request = ipnRequest(transactionId, 3000000L, 1006, "Người dùng huỷ thanh toán.");

        when(transactionRepository.findByGatewayTransId("4123456789")).thenReturn(Optional.empty());
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        paymentService.handleMomoIpn(request);

        assertEquals(TransactionStatus.FAILED, transaction.getStatus());
        verify(eventPublisher).publishFailed(any(PaymentFailedEvent.class));
        verify(eventPublisher, never()).publishSuccess(any());
    }

    @Test
    void handleMomoIpn_throwsBadRequest_whenSignatureInvalid() {
        UUID transactionId = UUID.randomUUID();
        MomoIpnRequest valid = ipnRequest(transactionId, 3000000L, 0, "Successful.");
        MomoIpnRequest tampered = new MomoIpnRequest(valid.partnerCode(), valid.orderId(), valid.requestId(),
                999L, valid.orderInfo(), valid.orderType(), valid.transId(), valid.resultCode(), valid.message(),
                valid.payType(), valid.responseTime(), valid.extraData(), valid.signature());

        assertThrows(BadRequestException.class, () -> paymentService.handleMomoIpn(tampered));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void handleMomoIpn_idempotent_whenTransIdAlreadyProcessed() {
        UUID transactionId = UUID.randomUUID();
        MomoIpnRequest request = ipnRequest(transactionId, 3000000L, 0, "Successful.");
        when(transactionRepository.findByGatewayTransId("4123456789"))
                .thenReturn(Optional.of(Transaction.builder().build()));

        paymentService.handleMomoIpn(request);

        verify(transactionRepository, never()).findById(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void handleMomoIpn_throwsBadRequest_whenAmountMismatch() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(transactionId).bookingId(bookingId).amount(new BigDecimal("3000000"))
                .paymentMethod("MOMO").status(TransactionStatus.PENDING).build();
        MomoIpnRequest request = ipnRequest(transactionId, 999000L, 0, "Successful.");

        when(transactionRepository.findByGatewayTransId("4123456789")).thenReturn(Optional.empty());
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        assertThrows(BadRequestException.class, () -> paymentService.handleMomoIpn(request));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void handleMomoIpn_noop_whenTransactionNotFound() {
        UUID transactionId = UUID.randomUUID();
        MomoIpnRequest request = ipnRequest(transactionId, 3000000L, 0, "Successful.");
        when(transactionRepository.findByGatewayTransId("4123456789")).thenReturn(Optional.empty());
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        paymentService.handleMomoIpn(request);

        verifyNoInteractions(eventPublisher);
    }

    private BookingRefundRequestedEvent refundRequestedEvent(UUID transactionId) {
        return BookingRefundRequestedEvent.builder()
                .eventType("booking.refund-requested")
                .bookingId(bookingId)
                .transactionId(transactionId)
                .amount(new BigDecimal("3000000"))
                .gatewayTransId("4123456789")
                .reason("Booking đã bị hủy do hết hạn giữ chỗ")
                .build();
    }

    @Test
    void processRefund_succeeds_marksRefundedAndPublishesEvent() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(transactionId).bookingId(bookingId).amount(new BigDecimal("3000000"))
                .paymentMethod("MOMO").status(TransactionStatus.SUCCESS).gatewayTransId("4123456789").build();
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        MomoRefundResponse response = new MomoRefundResponse(
                "MOMOPARTNER", "orderId", "reqId", 3000000L, 0, "Successful.", 4123456789L);
        when(momoClient.refund(eq(bookingId), eq(new BigDecimal("3000000")), eq(4123456789L), anyString()))
                .thenReturn(response);

        paymentService.processRefund(refundRequestedEvent(transactionId));

        assertEquals(TransactionStatus.REFUNDED, transaction.getStatus());
        verify(eventPublisher).publishRefunded(any(PaymentRefundedEvent.class));
    }

    @Test
    void processRefund_isNoop_whenTransactionNotFound() {
        UUID transactionId = UUID.randomUUID();
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        paymentService.processRefund(refundRequestedEvent(transactionId));

        verifyNoInteractions(momoClient, eventPublisher);
    }

    @Test
    void processRefund_isIdempotent_whenAlreadyRefunded() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(transactionId).bookingId(bookingId).amount(new BigDecimal("3000000"))
                .paymentMethod("MOMO").status(TransactionStatus.REFUNDED).gatewayTransId("4123456789").build();
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        paymentService.processRefund(refundRequestedEvent(transactionId));

        verifyNoInteractions(momoClient, eventPublisher);
    }

    @Test
    void processRefund_skips_whenTransactionNotSuccess() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(transactionId).bookingId(bookingId).amount(new BigDecimal("3000000"))
                .paymentMethod("MOMO").status(TransactionStatus.PENDING).build();
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        paymentService.processRefund(refundRequestedEvent(transactionId));

        verifyNoInteractions(momoClient, eventPublisher);
    }

    @Test
    void processRefund_doesNotMarkRefunded_whenMomoRejects() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(transactionId).bookingId(bookingId).amount(new BigDecimal("3000000"))
                .paymentMethod("MOMO").status(TransactionStatus.SUCCESS).gatewayTransId("4123456789").build();
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        MomoRefundResponse rejected = new MomoRefundResponse(
                "MOMOPARTNER", "orderId", "reqId", 3000000L, 99, "Refund rejected.", null);
        when(momoClient.refund(eq(bookingId), eq(new BigDecimal("3000000")), eq(4123456789L), anyString()))
                .thenReturn(rejected);

        paymentService.processRefund(refundRequestedEvent(transactionId));

        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        verifyNoInteractions(eventPublisher);
        verify(transactionRepository, never()).save(any());
    }
}
