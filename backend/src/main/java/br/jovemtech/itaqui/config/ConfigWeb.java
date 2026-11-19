package br.jovemtech.itaqui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.jovemtech.itaqui.calculo.MotorCalculo;
import br.jovemtech.itaqui.calculo.ProjecaoPorIndice;

@Configuration
public class ConfigWeb implements WebMvcConfigurer {

    private final PropriedadesApp props;

    public ConfigWeb(PropriedadesApp props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (props.cors().origens().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(props.cors().origens().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }

    /** O motor é Java puro; o Spring só o instancia com a regra de projeção escolhida. */
    @Bean
    public MotorCalculo motorCalculo() {
        return new MotorCalculo(new ProjecaoPorIndice());
    }
}
