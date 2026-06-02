package com.hospitalclaims.repository;

import com.hospitalclaims.db.DatabaseConnection;
import com.hospitalclaims.model.Patient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC repository for patient records. */
public class PatientRepository extends RepositorySupport implements CrudRepository<Patient, String> {
    public PatientRepository(DatabaseConnection databaseConnection) {
        super(databaseConnection);
    }

    @Override
    public void save(Patient patient) throws SQLException {
        String sql = """
                INSERT INTO Patient
                (patientID, firstname, surname, postcode, address, phone, email, insuranceID, primaryCareDoctorID)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patient.getPatientId());
            statement.setString(2, patient.getFirstName());
            statement.setString(3, patient.getSurname());
            statement.setString(4, patient.getPostcode());
            statement.setString(5, patient.getAddress());
            statement.setString(6, patient.getPhone());
            statement.setString(7, patient.getEmail());
            statement.setString(8, patient.getInsuranceId());
            statement.setString(9, patient.getPrimaryCareDoctorId());
            statement.executeUpdate();
        }
    }

    public void update(Patient patient) throws SQLException {
        String sql = """
                UPDATE Patient
                SET firstname = ?, surname = ?, postcode = ?, address = ?, phone = ?, email = ?, insuranceID = ?, primaryCareDoctorID = ?
                WHERE patientID = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patient.getFirstName());
            statement.setString(2, patient.getSurname());
            statement.setString(3, patient.getPostcode());
            statement.setString(4, patient.getAddress());
            statement.setString(5, patient.getPhone());
            statement.setString(6, patient.getEmail());
            statement.setString(7, patient.getInsuranceId());
            statement.setString(8, patient.getPrimaryCareDoctorId());
            statement.setString(9, patient.getPatientId());
            statement.executeUpdate();
        }
    }

    public void deleteById(String patientId) throws SQLException {
        String sql = "DELETE FROM Patient WHERE patientID = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Patient> findById(String patientId) throws SQLException {
        String sql = """
                SELECT patientID, firstname, surname, postcode, address, phone, email, insuranceID, primaryCareDoctorID
                FROM Patient
                WHERE patientID = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Patient> findAll() throws SQLException {
        String sql = """
                SELECT patientID, firstname, surname, postcode, address, phone, email, insuranceID, primaryCareDoctorID
                FROM Patient
                ORDER BY surname, firstname, patientID
                """;
        List<Patient> patients = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                patients.add(mapRow(resultSet));
            }
        }
        return patients;
    }

    public List<Patient> findBySurname(String surname) throws SQLException {
        String sql = """
                SELECT patientID, firstname, surname, postcode, address, phone, email, insuranceID, primaryCareDoctorID
                FROM Patient
                WHERE LOWER(surname) LIKE ?
                ORDER BY firstname, patientID
                """;
        List<Patient> patients = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + surname.trim().toLowerCase() + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    patients.add(mapRow(resultSet));
                }
            }
        }
        return patients;
    }

    /** Maps one result-set row into the domain model. */
    private Patient mapRow(ResultSet resultSet) throws SQLException {
        return new Patient(
                clean(resultSet.getString("patientID")),
                clean(resultSet.getString("firstname")),
                clean(resultSet.getString("surname")),
                clean(resultSet.getString("postcode")),
                clean(resultSet.getString("address")),
                clean(resultSet.getString("phone")),
                clean(resultSet.getString("email")),
                clean(resultSet.getString("insuranceID")),
                clean(resultSet.getString("primaryCareDoctorID"))
        );
    }
}
