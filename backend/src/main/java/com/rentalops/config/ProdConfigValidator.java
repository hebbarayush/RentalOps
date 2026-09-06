package com.rentalops.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fails startup — before any bean is constructed — if the {@code prod} profile is active but a
 * security-critical setting is missing or still a development placeholder. Registered as an
 * {@link EnvironmentPostProcessor} in {@code META-INF/spring.factories} so it runs ahead of
 * {@code JwtService} et al. and produces one clear, aggregated error instead of a downstream
 * stack trace.
 */
public class ProdConfigValidator implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProdConfigValidator.class);

    /** The zero-config fallback baked into application.yml for local dev — must never reach prod. */
    private static final String DEV_JWT_SECRET =
            "RentalOpsDevelopmentSecretKeyMustBeChangedBeforeProduction123456789";
    private static final int MIN_SECRET_LENGTH = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        // Profiles aren't fully resolved this early — read the raw property (fed by
        // SPRING_PROFILES_ACTIVE / spring.profiles.active) rather than env.matchesProfiles().
        String activeProfiles = env.getProperty("spring.profiles.active", "");
        boolean prod = Arrays.stream(activeProfiles.split(","))
                .map(String::trim).anyMatch("prod"::equalsIgnoreCase);
        if (!prod) {
            return;
        }

        String jwtSecret = env.getProperty("app.jwt.secret", env.getProperty("JWT_SECRET", ""));
        String corsOrigins = env.getProperty("app.cors.allowed-origins",
                env.getProperty("CORS_ALLOWED_ORIGINS", ""));
        String dbUrl = env.getProperty("spring.datasource.url", env.getProperty("DB_URL", ""));
        boolean redisEnabled = Boolean.parseBoolean(
                env.getProperty("app.redis.enabled", env.getProperty("REDIS_ENABLED", "false")));
        String cacheType = env.getProperty("spring.cache.type",
                env.getProperty("SPRING_CACHE_TYPE", "caffeine"));

        List<String> errors = new ArrayList<>();

        if (isBlank(jwtSecret)) {
            errors.add("JWT_SECRET is not set.");
        } else if (jwtSecret.equals(DEV_JWT_SECRET)) {
            errors.add("JWT_SECRET is still the development placeholder — generate a fresh random value.");
        } else if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            errors.add("JWT_SECRET must be at least " + MIN_SECRET_LENGTH + " characters (got "
                    + jwtSecret.length() + ").");
        }

        if (isBlank(corsOrigins)) {
            errors.add("CORS_ALLOWED_ORIGINS is not set — the frontend origin must be explicit in prod.");
        } else {
            List<String> origins = Arrays.stream(corsOrigins.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (origins.stream().anyMatch(o -> o.contains("localhost") || o.contains("127.0.0.1"))) {
                errors.add("CORS_ALLOWED_ORIGINS contains a localhost origin: " + corsOrigins);
            }
        }

        if (isBlank(dbUrl)) {
            errors.add("DB_URL is not set.");
        }

        if (redisEnabled && !"redis".equalsIgnoreCase(cacheType)) {
            log.warn("REDIS_ENABLED=true but SPRING_CACHE_TYPE={} — the dashboard cache is still "
                    + "in-process. Set SPRING_CACHE_TYPE=redis to share it across instances.", cacheType);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Refusing to start with profile 'prod':\n  - "
                    + String.join("\n  - ", errors)
                    + "\nSet these via environment variables (see docs/DEPLOYMENT.md).");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
