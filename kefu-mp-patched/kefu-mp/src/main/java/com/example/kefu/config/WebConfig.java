// src/main/java/com/example/kefu/config/WebConfig.java
package com.example.kefu.config;

import com.example.kefu.security.HeaderAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final HeaderAuthInterceptor headerAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(headerAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/refresh",
                        "/error",
                        "/actuator/**",
                        "/file/**",
                        "/favicon.ico",
                        "/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.svg"
                );
        // 注：/auth/logout 不必排除；若账户被禁用，它也会被 401 拦下并强制登出
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/file/**")
                .addResourceLocations("file:/www/wwwroot/file/")
                .setCachePeriod(60 * 60 * 24 * 30);
    }
}
