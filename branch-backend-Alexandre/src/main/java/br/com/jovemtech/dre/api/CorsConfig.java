package br.com.jovemtech.dre.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS apenas para front-ends locais em desenvolvimento; não habilita origens externas. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:5173","http://localhost:3000","http://localhost:4200",
                "http://127.0.0.1:5173","http://127.0.0.1:3000")
            .allowedMethods("GET","POST","PUT","OPTIONS")
            .allowedHeaders("*");
    }
}
