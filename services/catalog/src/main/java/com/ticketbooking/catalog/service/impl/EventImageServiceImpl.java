package com.ticketbooking.catalog.service.impl;

import com.ticketbooking.catalog.service.EventImageService;
import com.ticketbooking.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class EventImageServiceImpl implements EventImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String URL_PREFIX = "/uploads/events/";

    private final Path storageDir;

    public EventImageServiceImpl(@Value("${catalog.upload.event-images-dir:uploads/events}") String eventImagesDir) {
        this.storageDir = Path.of(eventImagesDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo thư mục lưu ảnh sự kiện: " + storageDir, e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ảnh để tải lên.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Chỉ chấp nhận ảnh định dạng JPEG, PNG hoặc WEBP.");
        }

        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String filename = UUID.randomUUID() + extension;

        try {
            Path target = storageDir.resolve(filename);
            Files.copy(file.getInputStream(), target);
            return URL_PREFIX + filename;
        } catch (IOException e) {
            log.error("Lưu ảnh sự kiện thất bại: {}", e.getMessage());
            throw new IllegalStateException("Không thể lưu ảnh sự kiện.", e);
        }
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }

        String filename = url.substring(URL_PREFIX.length());
        Path target = storageDir.resolve(filename).normalize();
        if (!target.startsWith(storageDir)) {
            log.warn("Bỏ qua xóa ảnh sự kiện với đường dẫn bất thường: {}", url);
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Không thể xóa ảnh sự kiện cũ {}: {}", url, e.getMessage());
        }
    }
}
