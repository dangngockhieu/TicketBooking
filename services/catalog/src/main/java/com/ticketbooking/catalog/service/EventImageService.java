package com.ticketbooking.catalog.service;

import org.springframework.web.multipart.MultipartFile;

public interface EventImageService {

    /**
     * Lưu ảnh banner sự kiện vào thư mục cấu hình bởi
     * {@code catalog.upload.event-images-dir} và trả về URL công khai
     * (phục vụ qua WebConfig#addResourceHandlers dưới /uploads/events/**).
     */
    String store(MultipartFile file);

    /**
     * Xóa ảnh cũ theo URL đã lưu trước đó (dạng {@code /uploads/events/<file>})
     * — dùng khi Organizer thay banner mới, tránh tồn file rác trên đĩa. Im
     * lặng bỏ qua nếu url null/không thuộc thư mục quản lý (vd URL ngoài do
     * client tự nhập trước khi có tính năng upload).
     */
    void delete(String url);
}
