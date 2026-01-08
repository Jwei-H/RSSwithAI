package com.jingwei.rsswithai.config;

import com.jingwei.rsswithai.interfaces.admin.AdminJwtInterceptor;
import com.jingwei.rsswithai.interfaces.front.FrontJwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminJwtInterceptor adminJwtInterceptor;
    private final FrontJwtInterceptor frontJwtInterceptor;

    public WebConfig(AdminJwtInterceptor adminJwtInterceptor, FrontJwtInterceptor frontJwtInterceptor) {
        this.adminJwtInterceptor = adminJwtInterceptor;
        this.frontJwtInterceptor = frontJwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminJwtInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/login");

        registry.addInterceptor(frontJwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/**", "/api/login", "/api/register")
        ;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // 允许所有来源
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}