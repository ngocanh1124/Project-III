package prj3.example.Prj3.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class MultiPortConfig {

    @Value("${server.port:8080}")
    private int primaryPort;

    @Value("${server.secondary-port:8081}")
    private int secondaryPort;

    

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> secondaryPortCustomizer() {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(secondaryPort);
            connector.setScheme("http");
            connector.setSecure(false);

            
            connector.setProperty("maxConnections", "2000");
            connector.setProperty("acceptCount", "500");
            connector.setProperty("connectionTimeout", "20000");
            connector.setProperty("maxThreads", "200");
            connector.setProperty("minSpareThreads", "20");

            factory.addAdditionalTomcatConnectors(connector);
        };
    }

    

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> primaryPortCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            connector.setProperty("maxThreads", "300");
            connector.setProperty("minSpareThreads", "30");
            connector.setProperty("maxConnections", "5000");
            connector.setProperty("acceptCount", "1000");
            connector.setProperty("connectionTimeout", "30000");
        });
    }

    

    @Bean(name = "asyncFaceExecutor")
    public Executor asyncFaceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("face-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
