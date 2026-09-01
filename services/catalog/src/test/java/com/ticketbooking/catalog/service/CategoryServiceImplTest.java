package com.ticketbooking.catalog.service;

import com.ticketbooking.catalog.dto.request.CategoryRequest;
import com.ticketbooking.catalog.dto.response.CategoryResponse;
import com.ticketbooking.catalog.entity.Category;
import com.ticketbooking.catalog.repository.CategoryRepository;
import com.ticketbooking.catalog.repository.EventRepository;
import com.ticketbooking.catalog.service.impl.CategoryServiceImpl;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private UUID categoryId;
    private Category existingCategory;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        existingCategory = Category.builder()
                .id(categoryId)
                .name("Âm nhạc")
                .slug("am-nhac")
                .build();
    }

    @Test
    void testCreate_Success() {
        CategoryRequest request = new CategoryRequest("Thể thao", "the-thao", null);
        when(categoryRepository.existsByName("Thể thao")).thenReturn(false);
        when(categoryRepository.existsBySlug("the-thao")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(UUID.randomUUID());
            return category;
        });

        CategoryResponse result = categoryService.create(request);

        assertEquals("Thể thao", result.name());
        assertEquals("the-thao", result.slug());
    }

    @Test
    void testCreate_DuplicateName_ThrowsConflict() {
        CategoryRequest request = new CategoryRequest("Âm nhạc", "am-nhac-2", null);
        when(categoryRepository.existsByName("Âm nhạc")).thenReturn(true);

        assertThrows(ConflictException.class, () -> categoryService.create(request));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void testCreate_DuplicateSlug_ThrowsConflict() {
        CategoryRequest request = new CategoryRequest("Âm nhạc mới", "am-nhac", null);
        when(categoryRepository.existsByName("Âm nhạc mới")).thenReturn(false);
        when(categoryRepository.existsBySlug("am-nhac")).thenReturn(true);

        assertThrows(ConflictException.class, () -> categoryService.create(request));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void testUpdate_NotFound_ThrowsResourceNotFound() {
        CategoryRequest request = new CategoryRequest("Âm nhạc", "am-nhac", null);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.update(categoryId, request));
    }

    @Test
    void testDelete_ReferencedByEvent_ThrowsConflict() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existingCategory));
        when(eventRepository.existsByCategoryId(categoryId)).thenReturn(true);

        assertThrows(ConflictException.class, () -> categoryService.delete(categoryId));
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void testDelete_Success() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existingCategory));
        when(eventRepository.existsByCategoryId(categoryId)).thenReturn(false);

        categoryService.delete(categoryId);

        verify(categoryRepository).delete(existingCategory);
    }
}
