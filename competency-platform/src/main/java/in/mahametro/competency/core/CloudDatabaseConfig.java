package in.mahametro.competency.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Universal Cloud & Local DataSource Auto-Configuration.
 * Automatically resolves database connectivity for:
 * 1. Railway, Render, Fly.io, Heroku (MYSQL_URL, MYSQL_PRIVATE_URL, DATABASE_URL)
 * 2. Railway multi-service linked variables (MYSQLHOST, MYSQLPORT, MYSQLUSER, MYSQLPASSWORD, MYSQLDATABASE)
 * 3. Standard Spring Boot variables (SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD)
 * 4. Local application.yml / application-dev.yml properties
 */
@Configuration
@Profile("!test")
public class CloudDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudDatabaseConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        HikariConfig config = new HikariConfig();

        // 1. Check for single connection string first
        String rawUrl = getFirstNonBlank(
                env.getProperty("SPRING_DATASOURCE_URL"),
                env.getProperty("MYSQL_URL"),
                env.getProperty("MYSQL_PRIVATE_URL"),
                env.getProperty("DATABASE_URL"),
                env.getProperty("DB_URL"),
                env.getProperty("spring.datasource.url")
        );

        String username = getFirstNonBlank(
                env.getProperty("SPRING_DATASOURCE_USERNAME"),
                env.getProperty("DB_USER"),
                env.getProperty("MYSQLUSER"),
                env.getProperty("spring.datasource.username")
        );

        String password = getFirstNonBlank(
                env.getProperty("SPRING_DATASOURCE_PASSWORD"),
                env.getProperty("DB_PASSWORD"),
                env.getProperty("MYSQLPASSWORD"),
                env.getProperty("spring.datasource.password")
        );

        String jdbcUrl;

        if (rawUrl != null && !rawUrl.isBlank()) {
            if (rawUrl.startsWith("mysql://")) {
                try {
                    URI uri = URI.create(rawUrl);
                    String host = uri.getHost();
                    int port = uri.getPort() > 0 ? uri.getPort() : 3306;
                    String path = uri.getPath();
                    if (path != null && path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    if (path == null || path.isBlank()) {
                        path = "railway";
                    }

                    if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                        String[] parts = uri.getUserInfo().split(":", 2);
                        if (username == null || username.isBlank()) {
                            username = parts[0];
                        }
                        if ((password == null || password.isBlank()) && parts.length > 1) {
                            password = parts[1];
                        }
                    }

                    jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + path
                            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
                    log.info("Detected Cloud MySQL connection string -> host: {}:{}, database: {}", host, port, path);
                } catch (Exception e) {
                    log.warn("Could not parse URI from rawUrl, falling back to prefix prepend: {}", e.getMessage());
                    jdbcUrl = "jdbc:" + rawUrl;
                }
            } else if (!rawUrl.startsWith("jdbc:")) {
                jdbcUrl = "jdbc:mysql://" + rawUrl;
            } else {
                jdbcUrl = rawUrl;
            }
        } else {
            // 2. Build from individual variables or defaults
            String host = env.getProperty("MYSQLHOST", "localhost");
            String port = env.getProperty("MYSQLPORT", "3306");
            String db = env.getProperty("MYSQLDATABASE", "competency");

            if ("localhost".equalsIgnoreCase(host) && isProd(env)) {
                log.warn("""
                        **************************************************************************************************
                        [CONFIGURATION NOTICE]
                        The application is running in PRODUCTION profile, but MYSQLHOST defaulted to 'localhost'.
                        If running on Railway, please link your MySQL service variables in Railway Web Service -> Variables:
                          MYSQLHOST = ${{MySQL.MYSQLHOST}}
                          MYSQLPORT = ${{MySQL.MYSQLPORT}}
                          MYSQLUSER = ${{MySQL.MYSQLUSER}}
                          MYSQLPASSWORD = ${{MySQL.MYSQLPASSWORD}}
                          MYSQLDATABASE = ${{MySQL.MYSQLDATABASE}}
                        (or link MYSQL_URL = ${{MySQL.MYSQL_URL}})
                        **************************************************************************************************""");
            }

            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
            log.info("Configured MySQL connection from component properties -> host: {}:{}, database: {}", host, port, db);
        }

        config.setJdbcUrl(jdbcUrl);
        if (username != null && !username.isBlank()) {
            config.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }

        config.setPoolName("MahaMetroHikariPool");
        config.setMaximumPoolSize(Integer.parseInt(env.getProperty("DB_POOL_SIZE", "20")));
        config.setMinimumIdle(Integer.parseInt(env.getProperty("DB_MIN_IDLE", "2")));
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(30000);
        config.setLeakDetectionThreshold(60000);

        log.info("HikariDataSource initialized with target: {}", maskUrl(jdbcUrl));
        return new HikariDataSource(config);
    }

    private static boolean isProd(Environment env) {
        String[] profiles = env.getActiveProfiles();
        for (String p : profiles) {
            if ("prod".equalsIgnoreCase(p)) return true;
        }
        return false;
    }

    private static String getFirstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String maskUrl(String url) {
        if (url == null) return "null";
        return url.replaceAll("(?i)password=[^&;]*", "password=***");
    }
}
