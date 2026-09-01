package com.ticketbooking.catalog.service.impl;

import com.ticketbooking.catalog.dto.request.CategoryRequest;
import com.ticketbooking.catalog.dto.response.CategoryResponse;
import com.ticketbooking.catalog.entity.Category;
import com.ticketbooking.catalog.repository.CategoryRepository;
import com.ticketbooking.catalog.repository.EventRepository;
import com.ticketbooking.catalog.service.CategoryService;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository, EventRepository eventRepository) {
        this.categoryRepository = categoryRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAll().stream().map(CategoryResponse::from).toList();
    }

    @Override
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new ConflictException("Tên danh mục \"" + request.name() + "\" đã tồn tại.");
        }
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Slug \"" + request.slug() + "\" đã tồn tại.");
        }

        Category category = Category.builder()
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .build();

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Override
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục."));

        if (categoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("Tên danh mục \"" + request.name() + "\" đã tồn tại.");
        }
        if (categoryRepository.existsBySlugAndIdNot(request.slug(), id)) {
            throw new ConflictException("Slug \"" + request.slug() + "\" đã tồn tại.");
        }

        category.setName(request.name());
        category.setSlug(request.slug());
        category.setDescription(request.description());

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Override
    public void delete(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục."));

        if (eventRepository.existsByCategoryId(id)) {
            throw new ConflictException("Danh mục đang được sử dụng bởi ít nhất một sự kiện, không thể xóa.");
        }

        categoryRepository.delete(category);
    }
}
