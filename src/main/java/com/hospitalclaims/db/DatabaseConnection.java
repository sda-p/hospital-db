package com.hospitalclaims.db;

import com.hospitalclaims.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Opens JDBC connections using the configured database settings. */
public class DatabaseConnection {
    private final DatabaseConfig config;

    public DatabaseConnection() {
        this.config = new DatabaseConfig();
    }

    /** Opens a new connection for one unit of work. */
    public Connection open() throws SQLException {
        return DriverManager.getConnection(
                config.getUrl(),
                config.getUsername(),
                config.getPassword()
        );
    }

    /** Performs a lightweight connectivity probe. */
    public boolean testConnection() {
        try (Connection ignored = open()) {
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }
}
