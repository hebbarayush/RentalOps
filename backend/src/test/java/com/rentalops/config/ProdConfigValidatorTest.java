package com.rentalops.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** The prod fail-fast checks — no Spring context, just the {@link EnvironmentPostProcessor}. */
class ProdConfigValidatorTest {

    private final ProdConfigValidator validator = new ProdConfigValidator();

    private void run(Map<String, Object> props) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", props));
        validator.postProcessEnvironment(env, null);
    }

    private static Map<String, Object> base() {
        Map<String, Object> m = new HashMap<>();
        m.put("spring.profiles.active", "prod");
        m.put("JWT_SECRET", "a-real-random-value-well-over-thirty-two-characters-long");
        m.put("CORS_ALLOWED_ORIGINS", "https://app.rentalops.example.com");
        m.put("DB_URL", "jdbc:postgresql://managed-host:5432/rentalops");
        return m;
    }

    @Test
    void passesWithGoodProdConfig() {
        assertThatCode(() -> run(base())).doesNotThrowAnyException();
    }

    @Test
    void ignoredWhenProfileIsNotProd() {
        Map<String, Object> m = base();
        m.put("spring.profiles.active", "default");
        m.remove("JWT_SECRET");
        m.remove("CORS_ALLOWED_ORIGINS");
        assertThatCode(() -> run(m)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingJwtSecret() {
        Map<String, Object> m = base();
        m.remove("JWT_SECRET");
        assertThatThrownBy(() -> run(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is not set");
    }

    @Test
    void rejectsDevPlaceholderSecret() {
        Map<String, Object> m = base();
        m.put("JWT_SECRET", "RentalOpsDevelopmentSecretKeyMustBeChangedBeforeProduction123456789");
        assertThatThrownBy(() -> run(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development placeholder");
    }

    @Test
    void rejectsShortSecret() {
        Map<String, Object> m = base();
        m.put("JWT_SECRET", "too-short");
        assertThatThrownBy(() -> run(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    void rejectsLocalhostCorsOrigin() {
        Map<String, Object> m = base();
        m.put("CORS_ALLOWED_ORIGINS", "http://localhost:5173");
        assertThatThrownBy(() -> run(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("localhost origin");
    }

    @Test
    void aggregatesEveryProblem() {
        Map<String, Object> m = base();
        m.remove("JWT_SECRET");
        m.put("CORS_ALLOWED_ORIGINS", "http://127.0.0.1:3000");
        m.remove("DB_URL");
        assertThatThrownBy(() -> run(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is not set")
                .hasMessageContaining("127.0.0.1")
                .hasMessageContaining("DB_URL is not set");
    }
}
