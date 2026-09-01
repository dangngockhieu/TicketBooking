package com.ticketbooking.catalog.repository;

import com.ticketbooking.catalog.dto.request.EventSearchFilter;
import com.ticketbooking.catalog.entity.Event;
import com.ticketbooking.catalog.enums.EventStatus;
import org.springframework.data.jpa.domain.Specification;

public final class EventSpecifications {

    private EventSpecifications() {
    }

    /**
     * `GET /api/events` (public) LUÔN chỉ trả sự kiện PUBLISHED — không cho phép
     * client tự chọn status khác để tránh lộ sự kiện DRAFT/CANCELLED của
     * Organizer khác (docs/api-design.md §3.2 chỉ minh họa param status, không
     * có nghĩa client được toàn quyền chọn trạng thái ở endpoint public).
     */
    public static Specification<Event> matchesPublicFilter(EventSearchFilter filter) {
        return Specification
                .where(hasStatus(EventStatus.PUBLISHED))
                .and(hasCategorySlug(filter.categorySlug()))
                .and(startsFrom(filter.startFrom()))
                .and(startsTo(filter.startTo()))
                .and(hasLocation(filter.location()))
                .and(hasKeyword(filter.keyword()));
    }

    private static Specification<Event> hasStatus(EventStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Event> hasCategorySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.join("category").get("slug"), slug);
    }

    private static Specification<Event> startsFrom(java.time.Instant from) {
        if (from == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startTime"), from);
    }

    private static Specification<Event> startsTo(java.time.Instant to) {
        if (to == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startTime"), to);
    }

    private static Specification<Event> hasLocation(String location) {
        if (location == null || location.isBlank()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.like(cb.lower(root.get("location")), "%" + location.toLowerCase() + "%");
    }

    private static Specification<Event> hasKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%");
    }
}
