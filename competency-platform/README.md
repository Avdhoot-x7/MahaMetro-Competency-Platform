# Maha Metro Competency Assessment Platform (Java)

Rewrite of the Python v1.3 pilot (kept in the parent folder as the reference).

| Item | Choice |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 4.1 (Spring MVC, Spring Data JPA, Spring Security, Thymeleaf) |
| Database | MySQL 8, schema changes through Flyway (`src/main/resources/db/migration`) |
| Server | Apache Tomcat 11 (WAR), Ubuntu 20.04 or newer |
| Build | Maven |

## Quick build check (Windows)

Double-click `build.bat`. It needs only JDK 17 and internet; Maven is downloaded automatically. The result is written to `build-log.txt`.

## Run on a developer PC

Needs JDK 17, Maven and MySQL 8 with a user `competency` / `competency`.

```
mvn spring-boot:run
```

Then open http://localhost:8080 and sign in with ADMIN / Admin@12345 (development only; created on an empty database).

## Build and deploy to Tomcat

```
mvn clean package            # creates target/competency.war
```

Copy `target/competency.war` to Tomcat's `webapps/` folder. Set these in Tomcat's `bin/setenv.sh`:

```
export SPRING_PROFILES_ACTIVE=prod
export DB_URL='jdbc:mysql://<host>:3306/competency?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
export DB_USER=...
export DB_PASSWORD=...
```

Note: Spring Boot 4 needs Tomcat 11. The Tomcat package in Ubuntu's own repository is too old; install Tomcat 11 from tomcat.apache.org.

## Package layout (`in.mahametro.competency`)

`core` config and shared code, `auth` login, `employees` staff master, `access` roles and permissions,
`framework` competencies and role profiles, `questions` question bank and approval, `tests` scheduling and assembly,
`delivery` test player, `proctoring`, `scoring`, `development` training plans, `reporting` dashboards and exports.
