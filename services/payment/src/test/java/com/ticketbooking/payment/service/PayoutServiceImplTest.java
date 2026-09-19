package com.ticketbooking.payment.service;

import com.ticketbooking.common.exception.BadRequestException;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import com.ticketbooking.payment.client.CatalogClient;
import com.ticketbooking.payment.client.UserClient;
import com.ticketbooking.payment.client.dto.BankAccountDto;
import com.ticketbooking.payment.client.dto.CatalogEventDto;
import com.ticketbooking.payment.dto.request.CreatePayoutRequest;
import com.ticketbooking.payment.dto.request.UpdatePayoutStatusRequest;
import com.ticketbooking.payment.dto.response.PayoutResponse;
import com.ticketbooking.payment.entity.PayoutRequest;
import com.ticketbooking.payment.enums.PayoutSource;
import com.ticketbooking.payment.enums.PayoutStatus;
import com.ticketbooking.payment.momo.MomoClient;
import com.ticketbooking.payment.momo.dto.MomoDisburseResponse;
import com.ticketbooking.payment.repository.PayoutRequestRepository;
import com.ticketbooking.payment.repository.TransactionRepository;
import com.ticketbooking.payment.service.impl.PayoutServiceImpl;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutServiceImplTest {

    @Mock
    private PayoutRequestRepository payoutRequestRepository;

    @Mock
    private OrganizerWalletService organizerWalletService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private MomoClient momoClient;

    private PayoutServiceImpl payoutService;

    private static final String TOKEN = "Bearer test-token";
    private UUID organizerId;

    @BeforeEach
    void setUp() {
        payoutService = new PayoutServiceImpl(payoutRequestRepository, organizerWalletService,
                transactionRepository, userClient, catalogClient, momoClient);
        organizerId = UUID.randomUUID();
    }

    @Test
    void requestManualPayout_succeeds_whenBankVerifiedAndBalanceSufficient() {
        BankAccountDto bank = new BankAccountDto("Vietcombank", "0071000123456", "CONG TY ABC", true, Instant.now());
        when(userClient.getMyBankAccount(TOKEN)).thenReturn(bank);
        when(organizerWalletService.reserveForPayout(organizerId, new BigDecimal("500000"))).thenReturn(true);
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        PayoutResponse response = payoutService.requestManualPayout(
                organizerId, TOKEN, new CreatePayoutRequest(new BigDecimal("500000")));

        assertEquals(PayoutStatus.PENDING, response.status());
        assertEquals(PayoutSource.MANUAL, response.source());
        assertEquals("Vietcombank", response.bankName());
    }

    @Test
    void requestManualPayout_throwsConflict_whenBankNotVerified() {
        when(userClient.getMyBankAccount(TOKEN)).thenReturn(new BankAccountDto("VCB", "123", "ABC", false, null));

        assertThrows(ConflictException.class, () -> payoutService.requestManualPayout(
                organizerId, TOKEN, new CreatePayoutRequest(new BigDecimal("500000"))));
        verifyNoInteractions(organizerWalletService);
    }

    @Test
    void requestManualPayout_throwsConflict_whenInsufficientBalance() {
        when(userClient.getMyBankAccount(TOKEN)).thenReturn(new BankAccountDto("VCB", "123", "ABC", true, Instant.now()));
        when(organizerWalletService.reserveForPayout(eq(organizerId), any())).thenReturn(false);

        assertThrows(ConflictException.class, () -> payoutService.requestManualPayout(
                organizerId, TOKEN, new CreatePayoutRequest(new BigDecimal("999999999"))));
        verify(payoutRequestRepository, never()).save(any());
    }

    private PayoutRequest pendingPayout() {
        return PayoutRequest.builder().id(UUID.randomUUID()).organizerId(organizerId)
                .amount(new BigDecimal("500000")).bankName("VCB").bankAccountNumber("123")
                .bankAccountHolder("ABC").status(PayoutStatus.PENDING).source(PayoutSource.MANUAL).build();
    }

    @Test
    void updateStatus_approves_whenPending() {
        PayoutRequest payout = pendingPayout();
        when(payoutRequestRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(payoutRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PayoutResponse response = payoutService.updateStatus(payout.getId(),
                new UpdatePayoutStatusRequest(PayoutStatus.APPROVED, null));

        assertEquals(PayoutStatus.APPROVED, response.status());
    }

    @Test
    void updateStatus_rejects_andReleasesReservedFunds() {
        PayoutRequest payout = pendingPayout();
        when(payoutRequestRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(payoutRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        payoutService.updateStatus(payout.getId(), new UpdatePayoutStatusRequest(PayoutStatus.REJECTED, "Lý do"));

        verify(organizerWalletService).releaseReservedPayout(organizerId, new BigDecimal("500000"));
        assertEquals(PayoutStatus.REJECTED, payout.getStatus());
    }

    @Test
    void updateStatus_throwsBadRequest_whenRejectingWithoutReason() {
        PayoutRequest payout = pendingPayout();
        when(payoutRequestRepository.findById(payout.getId())).thenReturn(Optional.of(payout));

        assertThrows(BadRequestException.class, () -> payoutService.updateStatus(
                payout.getId(), new UpdatePayoutStatusRequest(PayoutStatus.REJECTED, " ")));
    }

    @Test
    void updateStatus_pays_callsDisburseAndMarksWalletPaid() {
        PayoutRequest payout = pendingPayout();
        payout.setStatus(PayoutStatus.APPROVED);
        when(payoutRequestRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(payoutRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MomoDisburseResponse response = new MomoDisburseResponse("orderId", "req-1", 0, "Successful.", 123L);
        when(momoClient.disburse(eq(payout.getId()), eq(new BigDecimal("500000")), eq("123"), anyString()))
                .thenReturn(response);

        PayoutResponse result = payoutService.updateStatus(payout.getId(),
                new UpdatePayoutStatusRequest(PayoutStatus.PAID, null));

        assertEquals(PayoutStatus.PAID, result.status());
        verify(organizerWalletService).markPayoutPaid(organizerId, new BigDecimal("500000"));
    }

    @Test
    void updateStatus_throwsConflict_whenPayingNonApproved() {
        PayoutRequest payout = pendingPayout();
        when(payoutRequestRepository.findById(payout.getId())).thenReturn(Optional.of(payout));

        assertThrows(ConflictException.class, () -> payoutService.updateStatus(
                payout.getId(), new UpdatePayoutStatusRequest(PayoutStatus.PAID, null)));
        verifyNoInteractions(momoClient);
    }

    @Test
    void updateStatus_throwsNotFound_whenPayoutMissing() {
        UUID id = UUID.randomUUID();
        when(payoutRequestRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> payoutService.updateStatus(
                id, new UpdatePayoutStatusRequest(PayoutStatus.APPROVED, null)));
    }

    @Test
    void createAutoPayouts_createsPendingPayout_whenCompletedAndBankVerified() {
        UUID eventId = UUID.randomUUID();
        when(transactionRepository.findDistinctEventIdsWithSuccessfulPayment()).thenReturn(List.of(eventId));
        when(payoutRequestRepository.existsByEventIdAndSource(eventId, PayoutSource.AUTO)).thenReturn(false);
        CatalogEventDto event = new CatalogEventDto(eventId, organizerId, "COMPLETED",
                Instant.now().minus(8, java.time.temporal.ChronoUnit.DAYS),
                new BigDecimal("0.05"), new BigDecimal("3000"));
        when(catalogClient.getEvent(eventId)).thenReturn(event);
        when(transactionRepository.sumSuccessAmountByEventId(eventId)).thenReturn(new BigDecimal("10000000"));
        when(transactionRepository.sumSuccessQuantityByEventId(eventId)).thenReturn(10);
        when(organizerWalletService.reserveForPayout(eq(organizerId), any())).thenReturn(true);
        when(userClient.getBankAccountByOrganizerId(organizerId))
                .thenReturn(new BankAccountDto("VCB", "123", "ABC", true, Instant.now()));

        payoutService.createAutoPayouts();

        ArgumentCaptor<PayoutRequest> captor = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(payoutRequestRepository).save(captor.capture());
        assertEquals(PayoutStatus.PENDING, captor.getValue().getStatus());
        assertEquals(PayoutSource.AUTO, captor.getValue().getSource());
        // netRevenue = 10,000,000 - 500,000 (5%) - 30,000 (3000*10) = 9,470,000
        assertEquals(0, new BigDecimal("9470000").compareTo(captor.getValue().getAmount()));
    }

    @Test
    void createAutoPayouts_createsHoldPayout_whenBankNotVerified() {
        UUID eventId = UUID.randomUUID();
        when(transactionRepository.findDistinctEventIdsWithSuccessfulPayment()).thenReturn(List.of(eventId));
        when(payoutRequestRepository.existsByEventIdAndSource(eventId, PayoutSource.AUTO)).thenReturn(false);
        CatalogEventDto event = new CatalogEventDto(eventId, organizerId, "COMPLETED",
                Instant.now().minus(8, java.time.temporal.ChronoUnit.DAYS),
                BigDecimal.ZERO, BigDecimal.ZERO);
        when(catalogClient.getEvent(eventId)).thenReturn(event);
        when(transactionRepository.sumSuccessAmountByEventId(eventId)).thenReturn(new BigDecimal("1000000"));
        when(transactionRepository.sumSuccessQuantityByEventId(eventId)).thenReturn(5);
        when(organizerWalletService.reserveForPayout(eq(organizerId), any())).thenReturn(true);
        when(userClient.getBankAccountByOrganizerId(organizerId)).thenReturn(null);

        payoutService.createAutoPayouts();

        ArgumentCaptor<PayoutRequest> captor = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(payoutRequestRepository).save(captor.capture());
        assertEquals(PayoutStatus.HOLD, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getReason());
    }

    @Test
    void createAutoPayouts_skips_whenEventNotYetSevenDaysPastEnd() {
        UUID eventId = UUID.randomUUID();
        when(transactionRepository.findDistinctEventIdsWithSuccessfulPayment()).thenReturn(List.of(eventId));
        when(payoutRequestRepository.existsByEventIdAndSource(eventId, PayoutSource.AUTO)).thenReturn(false);
        CatalogEventDto event = new CatalogEventDto(eventId, organizerId, "COMPLETED",
                Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS),
                new BigDecimal("0.05"), new BigDecimal("3000"));
        when(catalogClient.getEvent(eventId)).thenReturn(event);

        payoutService.createAutoPayouts();

        verify(payoutRequestRepository, never()).save(any());
        verifyNoInteractions(organizerWalletService);
    }

    @Test
    void createAutoPayouts_skips_whenAlreadyHasAutoPayout() {
        UUID eventId = UUID.randomUUID();
        when(transactionRepository.findDistinctEventIdsWithSuccessfulPayment()).thenReturn(List.of(eventId));
        when(payoutRequestRepository.existsByEventIdAndSource(eventId, PayoutSource.AUTO)).thenReturn(true);

        payoutService.createAutoPayouts();

        verifyNoInteractions(catalogClient);
        verify(payoutRequestRepository, never()).save(any());
    }
}
