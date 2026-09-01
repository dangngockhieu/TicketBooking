package com.ticketbooking.catalog.service;

import com.ticketbooking.catalog.dto.request.CategoryRequest;
import com.ticketbooking.catalog.dto.response.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    List<CategoryResponse> list();

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(UUID id, CategoryRequest request);

    void delete(UUID id);
}
