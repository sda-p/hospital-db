package com.hospitalclaims.repository;

import com.hospitalclaims.db.DatabaseConnection;
import com.hospitalclaims.model.Prescription;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC repository for prescriptions. */
public class PrescriptionRepository extends RepositorySupport implements CrudRepository<Prescription, String> {
    public PrescriptionRepository(DatabaseConnection databaseConnection) {
        super(databaseConnection);
    }

    @Override
    public void save(Prescription prescription) throws SQLException {
        String sql = """
                INSERT INTO Prescription
                (prescriptionID, dateprescribed, dosage, duration, comment, drugID, doctorID, patientID)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, prescription.getPrescriptionId());
            statement.setObject(2, prescription.getDatePrescribed());
            statement.setString(3, prescription.getDosage());
            statement.setString(4, prescription.getDuration());
            statement.setString(5, prescription.getComment());
            statement.setString(6, prescription.getDrugId());
            statement.setString(7, prescription.getDoctorId());
            statement.setString(8, prescription.getPatientId());
            statement.executeUpdate();
        }
    }

    public void update(Prescription prescription) throws SQLException {
        String sql = """
                UPDATE Prescription
                SET dateprescribed = ?, dosage = ?, duration = ?, comment = ?, drugID = ?, doctorID = ?, patientID = ?
                WHERE prescriptionID = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, prescription.getDatePrescribed());
            statement.setString(2, prescription.getDosage());
            statement.setString(3, prescription.getDuration());
            statement.setString(4, prescription.getComment());
            statement.setString(5, prescription.getDrugId());
            statement.setString(6, prescription.getDoctorId());
            statement.setString(7, prescription.getPatientId());
            statement.setString(8, prescription.getPrescriptionId());
            statement.executeUpdate();
        }
    }

    public void deleteById(String prescriptionId) throws SQLException {
        String sql = "DELETE FROM Prescription WHERE prescriptionID = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, prescriptionId);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Prescription> findById(String prescriptionId) throws SQLException {
        String sql = """
                SELECT prescriptionID, dateprescribed, dosage, duration, comment, drugID, doctorID, patientID
                FROM Prescription
                WHERE prescriptionID = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, prescriptionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Prescription> findAll() throws SQLException {
        String sql = """
                SELECT prescriptionID, dateprescribed, dosage, duration, comment, drugID, doctorID, patientID
                FROM Prescription
                ORDER BY dateprescribed DESC, prescriptionID
                """;
        List<Prescription> prescriptions = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                prescriptions.add(mapRow(resultSet));
            }
        }
        return prescriptions;
    }

    public List<Prescription> findByPatientId(String patientId) throws SQLException {
        String sql = """
                SELECT prescriptionID, dateprescribed, dosage, duration, comment, drugID, doctorID, patientID
                FROM Prescription
                WHERE patientID = ?
                ORDER BY dateprescribed DESC, prescriptionID
                """;
        List<Prescription> prescriptions = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    prescriptions.add(mapRow(resultSet));
                }
            }
        }
        return prescriptions;
    }

    /** Maps one result-set row into the domain model. */
    private Prescription mapRow(ResultSet resultSet) throws SQLException {
        return new Prescription(
                clean(resultSet.getString("prescriptionID")),
                resultSet.getObject("dateprescribed", java.time.LocalDate.class),
                clean(resultSet.getString("dosage")),
                clean(resultSet.getString("duration")),
                clean(resultSet.getString("comment")),
                clean(resultSet.getString("drugID")),
                clean(resultSet.getString("doctorID")),
                clean(resultSet.getString("patientID"))
        );
    }
}
