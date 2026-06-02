package com.hospitalclaims.integration;

import com.hospitalclaims.db.DatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

abstract class MysqlIntegrationTestSupport {
    private static final String DEFAULT_ADMIN_URL =
            "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DEFAULT_DATABASE_URL_PREFIX =
            "jdbc:mysql://localhost:3306/";
    private static final String JDBC_OPTIONS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    protected String databaseName;
    protected String databaseUrl;
    protected String username;
    protected String password;

    @BeforeEach
    void setUpMysqlDatabase() throws SQLException {
        username = System.getProperty("hospitalclaims.test.db.username",
                System.getenv().getOrDefault("HOSPITALCLAIMS_TEST_DB_USERNAME", "root"));
        password = System.getProperty("hospitalclaims.test.db.password",
                System.getenv().getOrDefault("HOSPITALCLAIMS_TEST_DB_PASSWORD", ""));

        String adminUrl = System.getProperty("hospitalclaims.test.db.adminUrl",
                System.getenv().getOrDefault("HOSPITALCLAIMS_TEST_DB_ADMIN_URL", DEFAULT_ADMIN_URL));
        String databaseUrlPrefix = System.getProperty("hospitalclaims.test.db.urlPrefix",
                System.getenv().getOrDefault("HOSPITALCLAIMS_TEST_DB_URL_PREFIX", DEFAULT_DATABASE_URL_PREFIX));

        try (Connection ignored = DriverManager.getConnection(adminUrl, username, password)) {
            databaseName = "hospital_claims_test_" + UUID.randomUUID().toString().replace("-", "");
            databaseUrl = databaseUrlPrefix + databaseName + JDBC_OPTIONS;
            recreateDatabase(adminUrl);
        } catch (SQLException exception) {
            Assumptions.assumeTrue(false, "MySQL integration tests require a reachable local MySQL instance.");
        }
    }

    @AfterEach
    void tearDownMysqlDatabase() throws SQLException {
        if (databaseName == null) {
            return;
        }

        String adminUrl = System.getProperty("hospitalclaims.test.db.adminUrl",
                System.getenv().getOrDefault("HOSPITALCLAIMS_TEST_DB_ADMIN_URL", DEFAULT_ADMIN_URL));
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
        }
    }

    protected DatabaseConnection createDatabaseConnection() {
        return new DatabaseConnection() {
            @Override
            public Connection open() throws SQLException {
                return DriverManager.getConnection(databaseUrl, username, password);
            }
        };
    }

    protected Connection openConnection() throws SQLException {
        return DriverManager.getConnection(databaseUrl, username, password);
    }

    protected void configureApplicationProperties() {
        System.setProperty("db.url", databaseUrl);
        System.setProperty("db.username", username);
        System.setProperty("db.password", password);
    }

    protected void clearApplicationProperties() {
        System.clearProperty("db.url");
        System.clearProperty("db.username");
        System.clearProperty("db.password");
    }

    private void recreateDatabase(String adminUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
            statement.execute("CREATE DATABASE `" + databaseName + "`");
        }

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE Insurance (
                        insuranceID varchar(20) NOT NULL,
                        company varchar(50) DEFAULT NULL,
                        address varchar(100) DEFAULT NULL,
                        phone varchar(20) DEFAULT NULL,
                        PRIMARY KEY (insuranceID)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE Doctor (
                        doctorID varchar(20) NOT NULL,
                        firstname varchar(20) DEFAULT NULL,
                        surname varchar(20) DEFAULT NULL,
                        address varchar(100) DEFAULT NULL,
                        phone varchar(20) DEFAULT NULL,
                        email varchar(50) DEFAULT NULL,
                        specialization varchar(50) DEFAULT NULL,
                        hospital varchar(100) DEFAULT NULL,
                        PRIMARY KEY (doctorID)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE Drug (
                        drugID varchar(20) NOT NULL,
                        drugname varchar(50) DEFAULT NULL,
                        sideeffects text,
                        purpose text,
                        PRIMARY KEY (drugID)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE Patient (
                        patientID varchar(20) NOT NULL,
                        firstname varchar(20) DEFAULT NULL,
                        surname varchar(30) DEFAULT NULL,
                        postcode varchar(20) DEFAULT NULL,
                        address varchar(100) DEFAULT NULL,
                        phone varchar(20) DEFAULT NULL,
                        email varchar(50) DEFAULT NULL,
                        insuranceID varchar(20) DEFAULT NULL,
                        primaryCareDoctorID varchar(20) DEFAULT NULL,
                        PRIMARY KEY (patientID),
                        KEY insuranceID (insuranceID),
                        KEY primaryCareDoctorID (primaryCareDoctorID),
                        CONSTRAINT Patient_ibfk_1 FOREIGN KEY (insuranceID) REFERENCES Insurance (insuranceID),
                        CONSTRAINT Patient_ibfk_2 FOREIGN KEY (primaryCareDoctorID) REFERENCES Doctor (doctorID)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE Prescription (
                        prescriptionID varchar(20) NOT NULL,
                        dateprescribed date DEFAULT NULL,
                        dosage varchar(100) DEFAULT NULL,
                        duration varchar(50) DEFAULT NULL,
                        comment varchar(255) DEFAULT NULL,
                        drugID varchar(20) DEFAULT NULL,
                        doctorID varchar(20) DEFAULT NULL,
                        patientID varchar(20) DEFAULT NULL,
                        PRIMARY KEY (prescriptionID),
                        KEY drugID (drugID),
                        KEY doctorID (doctorID),
                        KEY patientID (patientID),
                        CONSTRAINT Prescription_ibfk_1 FOREIGN KEY (drugID) REFERENCES Drug (drugID),
                        CONSTRAINT Prescription_ibfk_2 FOREIGN KEY (doctorID) REFERENCES Doctor (doctorID),
                        CONSTRAINT Prescription_ibfk_3 FOREIGN KEY (patientID) REFERENCES Patient (patientID)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE Claim (
                        claimID varchar(20) NOT NULL,
                        prescriptionID varchar(20) NOT NULL,
                        patientID varchar(20) NOT NULL,
                        insuranceID varchar(20) NOT NULL,
                        status varchar(30) NOT NULL,
                        createdDate date NOT NULL,
                        submittedDate date DEFAULT NULL,
                        reviewedBy varchar(50) DEFAULT NULL,
                        decisionDate date DEFAULT NULL,
                        decisionNotes varchar(255) DEFAULT NULL,
                        PRIMARY KEY (claimID),
                        UNIQUE KEY claim_prescription_unique (prescriptionID),
                        KEY claim_patient_idx (patientID),
                        KEY claim_insurance_idx (insuranceID),
                        CONSTRAINT Claim_ibfk_1 FOREIGN KEY (prescriptionID) REFERENCES Prescription (prescriptionID),
                        CONSTRAINT Claim_ibfk_2 FOREIGN KEY (patientID) REFERENCES Patient (patientID),
                        CONSTRAINT Claim_ibfk_3 FOREIGN KEY (insuranceID) REFERENCES Insurance (insuranceID)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE Visit (
                        patientID varchar(20) NOT NULL,
                        doctorID varchar(20) NOT NULL,
                        dateofvisit date NOT NULL,
                        symptoms varchar(255) DEFAULT NULL,
                        diagnosisID varchar(255) DEFAULT NULL,
                        PRIMARY KEY (patientID, doctorID, dateofvisit),
                        KEY doctorID (doctorID),
                        CONSTRAINT Visit_ibfk_1 FOREIGN KEY (patientID) REFERENCES Patient (patientID),
                        CONSTRAINT Visit_ibfk_2 FOREIGN KEY (doctorID) REFERENCES Doctor (doctorID)
                    )
                    """);
        }
    }
}
