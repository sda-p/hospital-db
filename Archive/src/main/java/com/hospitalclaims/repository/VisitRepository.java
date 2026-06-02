package com.hospitalclaims.repository;

import com.hospitalclaims.db.DatabaseConnection;
import com.hospitalclaims.model.Visit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC repository for visit rows keyed by patient, doctor, and date. */
public class VisitRepository extends RepositorySupport implements CrudRepository<Visit, VisitKey> {
    public VisitRepository(DatabaseConnection databaseConnection) {
        super(databaseConnection);
    }

    @Override
    public void save(Visit visit) throws SQLException {
        String sql = """
                INSERT INTO Visit (patientID, doctorID, dateofvisit, symptoms, diagnosisID)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, visit.getPatientId());
            statement.setString(2, visit.getDoctorId());
            statement.setObject(3, visit.getDateOfVisit());
            statement.setString(4, visit.getSymptoms());
            statement.setString(5, visit.getDiagnosisId());
            statement.executeUpdate();
        }
    }

    public void update(VisitKey originalKey, Visit visit) throws SQLException {
        String sql = """
                UPDATE Visit
                SET patientID = ?, doctorID = ?, dateofvisit = ?, symptoms = ?, diagnosisID = ?
                WHERE patientID = ? AND doctorID = ? AND dateofvisit = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, visit.getPatientId());
            statement.setString(2, visit.getDoctorId());
            statement.setObject(3, visit.getDateOfVisit());
            statement.setString(4, visit.getSymptoms());
            statement.setString(5, visit.getDiagnosisId());
            statement.setString(6, originalKey.patientId());
            statement.setString(7, originalKey.doctorId());
            statement.setObject(8, originalKey.dateOfVisit());
            statement.executeUpdate();
        }
    }

    public void deleteById(VisitKey visitKey) throws SQLException {
        String sql = "DELETE FROM Visit WHERE patientID = ? AND doctorID = ? AND dateofvisit = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, visitKey.patientId());
            statement.setString(2, visitKey.doctorId());
            statement.setObject(3, visitKey.dateOfVisit());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Visit> findById(VisitKey visitKey) throws SQLException {
        String sql = """
                SELECT patientID, doctorID, dateofvisit, symptoms, diagnosisID
                FROM Visit
                WHERE patientID = ? AND doctorID = ? AND dateofvisit = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, visitKey.patientId());
            statement.setString(2, visitKey.doctorId());
            statement.setObject(3, visitKey.dateOfVisit());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Visit> findAll() throws SQLException {
        String sql = """
                SELECT patientID, doctorID, dateofvisit, symptoms, diagnosisID
                FROM Visit
                ORDER BY dateofvisit DESC, patientID, doctorID
                """;
        List<Visit> visits = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                visits.add(mapRow(resultSet));
            }
        }
        return visits;
    }

    public List<Visit> findByPatientId(String patientId) throws SQLException {
        String sql = """
                SELECT patientID, doctorID, dateofvisit, symptoms, diagnosisID
                FROM Visit
                WHERE patientID = ?
                ORDER BY dateofvisit DESC, doctorID
                """;
        List<Visit> visits = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    visits.add(mapRow(resultSet));
                }
            }
        }
        return visits;
    }

    /** Maps one result-set row into the domain model. */
    private Visit mapRow(ResultSet resultSet) throws SQLException {
        return new Visit(
                clean(resultSet.getString("patientID")),
                clean(resultSet.getString("doctorID")),
                resultSet.getObject("dateofvisit", java.time.LocalDate.class),
                clean(resultSet.getString("symptoms")),
                clean(resultSet.getString("diagnosisID"))
        );
    }
}
