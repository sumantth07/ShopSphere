package com.shopsphere.config;

import com.shopsphere.storage.LocalImageStorage;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves uploaded images at /uploads/** (public, long-cached: file names are random and never reused). */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final LocalImageStorage storage;
    public WebConfig(LocalImageStorage storage) { this.storage = storage; }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(storage.directory().toUri().toString())
            .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }
}
