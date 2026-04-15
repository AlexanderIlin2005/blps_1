package ru.sashil.config;

import jakarta.resource.cci.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jca.support.LocalConnectionFactoryBean;
import ru.sashil.jca.AccountingManagedConnectionFactory;

@Configuration
public class JcaConfig {

    @Bean
    public LocalConnectionFactoryBean accountingConnectionFactory() {
        LocalConnectionFactoryBean factoryBean = new LocalConnectionFactoryBean();
        factoryBean.setManagedConnectionFactory(new AccountingManagedConnectionFactory());
        return factoryBean;
    }
}
