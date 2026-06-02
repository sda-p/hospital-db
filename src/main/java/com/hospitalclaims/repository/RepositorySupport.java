package com.hospitalclaims.repository;

import com.hospitalclaims.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;

/** Common JDBC helpers shared by repository implementations. */
public abstract class RepositorySupport {
    private final DatabaseConnection databaseConnection;

    protected RepositorySupport(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    /** Opens a fresh database connection for one repository operation. */
    protected Connection openConnection() throws SQLException {
        return databaseConnection.open();
    }

    /** Trims database string columns while preserving null values. */
    protected String clean(String value) {
        return value == null ? null : value.trim();
    }
}
