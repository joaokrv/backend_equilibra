package org.app_financeiro.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CorsConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void quandoPerfilTestBeanCorsNaoEhPrimario() {
        String[] names = context.getBeanNamesForType(CorsConfigurationSource.class);
        assertThat(names).hasSize(1);
        CorsConfigurationSource bean = context.getBean(CorsConfigurationSource.class);
        assertThat(bean).isNotNull();
    }
}
