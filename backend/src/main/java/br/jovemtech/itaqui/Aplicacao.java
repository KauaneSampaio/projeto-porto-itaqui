package br.jovemtech.itaqui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Aplicacao {

    public static void main(String[] args) {
        SpringApplication.run(Aplicacao.class, args);
    }
}
