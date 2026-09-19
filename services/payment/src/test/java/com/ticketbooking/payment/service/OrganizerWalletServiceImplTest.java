package com.ticketbooking.payment.service;

import com.ticketbooking.payment.entity.OrganizerWallet;
import com.ticketbooking.payment.repository.OrganizerWalletRepository;
import com.ticketbooking.payment.service.impl.OrganizerWalletServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizerWalletServiceImplTest {

    @Mock
    private OrganizerWalletRepository organizerWalletRepository;

    private OrganizerWalletServiceImpl walletService;

    @BeforeEach
    void setUp() {
        walletService = new OrganizerWalletServiceImpl(organizerWalletRepository);
    }

    @Test
    void creditForBooking_createsWallet_andDeductsCommissionAndFlatFee() {
        UUID organizerId = UUID.randomUUID();
        when(organizerWalletRepository.findByOrganizerId(organizerId)).thenReturn(Optional.empty());

        // gross 3,000,000 - commission 5% (150,000) - flatFee 3,000 * 2 vé (6,000) = 2,844,000
        walletService.creditForBooking(organizerId, new BigDecimal("3000000"),
                new BigDecimal("0.05"), new BigDecimal("3000"), 2);

        ArgumentCaptor<OrganizerWallet> captor = ArgumentCaptor.forClass(OrganizerWallet.class);
        verify(organizerWalletRepository).save(captor.capture());
        assertEquals(organizerId, captor.getValue().getOrganizerId());
        assertEquals(0, new BigDecimal("2844000").compareTo(captor.getValue().getAvailableBalance()));
    }

    @Test
    void creditForBooking_addsToExistingBalance() {
        UUID organizerId = UUID.randomUUID();
        OrganizerWallet existing = OrganizerWallet.builder()
                .organizerId(organizerId).availableBalance(new BigDecimal("1000000")).build();
        when(organizerWalletRepository.findByOrganizerId(organizerId)).thenReturn(Optional.of(existing));

        walletService.creditForBooking(organizerId, new BigDecimal("100000"),
                BigDecimal.ZERO, BigDecimal.ZERO, 1);

        assertEquals(0, new BigDecimal("1100000").compareTo(existing.getAvailableBalance()));
    }

    @Test
    void creditForBooking_clampsAtZero_whenFeesExceedGrossAmount() {
        UUID organizerId = UUID.randomUUID();
        when(organizerWalletRepository.findByOrganizerId(organizerId)).thenReturn(Optional.empty());

        walletService.creditForBooking(organizerId, new BigDecimal("1000"),
                BigDecimal.ZERO, new BigDecimal("5000"), 1);

        ArgumentCaptor<OrganizerWallet> captor = ArgumentCaptor.forClass(OrganizerWallet.class);
        verify(organizerWalletRepository).save(captor.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getAvailableBalance()));
    }
}
