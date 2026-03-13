package ru.sashil.config;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jndi.JndiObjectFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.naming.NamingException;
import javax.sql.DataSource;

@Configuration
public class JtaConfig {

    @Bean
    public UserTransaction userTransaction() throws NamingException {
        JndiObjectFactoryBean factoryBean = new JndiObjectFactoryBean();
        factoryBean.setJndiName("java:comp/UserTransaction");
        factoryBean.afterPropertiesSet();
        return (UserTransaction) factoryBean.getObject();
    }

    @Bean
    public TransactionManager jakartaTransactionManager() throws NamingException {
        JndiObjectFactoryBean factoryBean = new JndiObjectFactoryBean();
        factoryBean.setJndiName("java:jboss/TransactionManager");
        factoryBean.afterPropertiesSet();
        return (TransactionManager) factoryBean.getObject();
    }

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(UserTransaction userTransaction, TransactionManager jakartaTransactionManager) {
        JtaTransactionManager jtaManager = new JtaTransactionManager();
        jtaManager.setUserTransaction(userTransaction);
        jtaManager.setTransactionManager(jakartaTransactionManager);
        jtaManager.setAllowCustomIsolationLevels(true);
        return jtaManager;
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
