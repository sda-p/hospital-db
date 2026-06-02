package com.hospitalclaims.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Loads database settings from properties with system and env overrides. */
public class DatabaseConfig {
    private final Properties properties = new Properties();

    public DatabaseConfig() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("application.properties was not found on the classpath.");
            }
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load database configuration.", exception);
        }
    }

    /** Returns the configured JDBC URL. */
    public String getUrl() {
        return getConfigValue("db.url", "DB_URL", false);
    }

    /** Returns the configured database username. */
    public String getUsername() {
        return getConfigValue("db.username", "DB_USERNAME", false);
    }

    /** Returns the configured database password. */
    public String getPassword() {
        return getConfigValue("db.password", "DB_PASSWORD", true);
    }

    /** Resolves a setting using system properties, then env vars, then the file value. */
    private String getConfigValue(String propertyKey, String environmentKey, boolean allowBlank) {
        String systemValue = System.getProperty(propertyKey);
        if (systemValue != null && (allowBlank || !systemValue.isBlank())) {
            return allowBlank ? systemValue : systemValue.trim();
        }

        String environmentValue = System.getenv(environmentKey);
        if (environmentValue != null && (allowBlank || !environmentValue.isBlank())) {
            return allowBlank ? environmentValue : environmentValue.trim();
        }

        return properties.getProperty(propertyKey);
    }
}
