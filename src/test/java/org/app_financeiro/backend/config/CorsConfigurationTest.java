package org.app_financeiro.backend.config;

import org.app_financeiro.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigurationTest extends AbstractIntegrationTest {

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
