package app.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
@ComponentScan(basePackages = "app")                   // quét service/dao
@EnableJpaRepositories(basePackages = "app.repository")// quét repository
public class AppConfig {

    /* 1 ▪ DataSource */
    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl("jdbc:mysql://localhost:3306/chatdb?serverTimezone=UTC&useSSL=false");
        ds.setUsername("root");
        return ds;
    }

    /* 2 ▪ EntityManagerFactory  (tên MẶC ĐỊNH = entityManagerFactory) */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource ds) {

        LocalContainerEntityManagerFactoryBean f =
                new LocalContainerEntityManagerFactoryBean();
        f.setDataSource(ds);
        f.setPackagesToScan("app.model");          // entity package
        f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties jpa = new Properties();
        jpa.put("hibernate.hbm2ddl.auto", "update");  // tạo bảng tự động
        jpa.put("hibernate.show_sql",      "false");
        jpa.put("hibernate.format_sql",    "false");
        f.setJpaProperties(jpa);
        return f;
    }

    /* 3 ▪ TransactionManager (Spring sẽ tự tiêm entityManagerFactory) */
    @Bean
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}