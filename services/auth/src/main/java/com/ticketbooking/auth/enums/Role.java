package com.ticketbooking.auth.enums;

public enum Role {
    ADMIN,
    ORGANIZER,
    CUSTOMER;

    public String withPrefix() {
        return "ROLE_" + this.name();
    }
}
