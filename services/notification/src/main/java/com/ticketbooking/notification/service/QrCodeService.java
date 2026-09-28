package com.ticketbooking.notification.service;

public interface QrCodeService {

    /** Sinh ảnh QR PNG (300x300) mã hoá {@code data} — dùng để nhúng inline vào email E-Ticket. */
    byte[] generatePng(String data);
}
