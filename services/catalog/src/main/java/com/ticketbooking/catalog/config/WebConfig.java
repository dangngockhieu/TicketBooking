package com.ticketbooking.catalog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${catalog.upload.event-images-dir:uploads/events}")
    private String eventImagesDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + new File(eventImagesDir).getAbsolutePath() + File.separator;
        registry.addResourceHandler("/uploads/events/**")
                .addResourceLocations(location);
    }
}
