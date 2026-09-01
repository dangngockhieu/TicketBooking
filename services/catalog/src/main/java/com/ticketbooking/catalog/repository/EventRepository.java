package com.ticketbooking.catalog.repository;

import com.ticketbooking.catalog.entity.Event;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    @EntityGraph(attributePaths = {"category", "ticketClasses"})
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findWithDetailsById(@Param("id") UUID id);

    boolean existsByCategoryId(UUID categoryId);
}
