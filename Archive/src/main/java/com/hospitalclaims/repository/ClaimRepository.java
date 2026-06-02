package com.hospitalclaims.repository;

import com.hospitalclaims.db.DatabaseConnection;
import com.hospitalclaims.model.Claim;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC repository for claim workflow records. */
public class ClaimRepository extends RepositorySupport implements CrudRepository<Claim, String> {
    public ClaimRepository(DatabaseConnection databaseConnection) {
        super(databaseConnection);
    }

    @Override
    public void save(Claim claim) throws SQLException {
        String sql = """
                INSERT INTO Claim
                (claimID, prescriptionID, patientID, insuranceID, status, createdDate, submittedDate, reviewedBy, decisionDate, decisionNotes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, claim.getClaimId());
            statement.setString(2, claim.getPrescriptionId());
            statement.setString(3, claim.getPatientId());
            statement.setString(4, claim.getInsuranceId());
            statement.setString(5, claim.getStatus());
            statement.setObject(6, claim.getCreatedDate());
            statement.setObject(7, claim.getSubmittedDate());
            statement.setString(8, claim.getReviewedBy());
            statement.setObject(9, claim.getDecisionDate());
            statement.setString(10, claim.getDecisionNotes());
            statement.executeUpdate();
        }
    }

    public void update(Claim claim) throws SQLException {
        String sql = """
                UPDATE Claim
                SET prescriptionID = ?, patientID = ?, insuranceID = ?, status = ?, createdDate = ?, submittedDate = ?, reviewedBy = ?, decisionDate = ?, decisionNotes = ?
                WHERE claimID = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, claim.getPrescriptionId());
            statement.setString(2, claim.getPatientId());
            statement.setString(3, claim.getInsuranceId());
            statement.setString(4, claim.getStatus());
            statement.setObject(5, claim.getCreatedDate());
            statement.setObject(6, claim.getSubmittedDate());
            statement.setString(7, claim.getReviewedBy());
            statement.setObject(8, claim.getDecisionDate());
            statement.setString(9, claim.getDecisionNotes());
            statement.setString(10, claim.getClaimId());
            statement.executeUpdate();
        }
    }

    public void deleteById(String claimId) throws SQLException {
        String sql = "DELETE FROM Claim WHERE claimID = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, claimId);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Claim> findById(String claimId) throws SQLException {
        String sql = """
                SELECT claimID, prescriptionID, patientID, insuranceID, status, createdDate, submittedDate, reviewedBy, decisionDate, decisionNotes
                FROM Claim
                WHERE claimID = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, claimId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    public Optional<Claim> findByPrescriptionId(String prescriptionId) throws SQLException {
        String sql = """
                SELECT claimID, prescriptionID, patientID, insuranceID, status, createdDate, submittedDate, reviewedBy, decisionDate, decisionNotes
                FROM Claim
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
    public List<Claim> findAll() throws SQLException {
        String sql = """
                SELECT claimID, prescriptionID, patientID, insuranceID, status, createdDate, submittedDate, reviewedBy, decisionDate, decisionNotes
                FROM Claim
                ORDER BY createdDate DESC, claimID
                """;
        List<Claim> claims = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                claims.add(mapRow(resultSet));
            }
        }
        return claims;
    }

    public List<Claim> findByPatientId(String patientId) throws SQLException {
        String sql = """
                SELECT claimID, prescriptionID, patientID, insuranceID, status, createdDate, submittedDate, reviewedBy, decisionDate, decisionNotes
                FROM Claim
                WHERE patientID = ?
                ORDER BY createdDate DESC, claimID
                """;
        List<Claim> claims = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    claims.add(mapRow(resultSet));
                }
            }
        }
        return claims;
    }

    /** Maps one result-set row into the domain model. */
    private Claim mapRow(ResultSet resultSet) throws SQLException {
        return new Claim(
                clean(resultSet.getString("claimID")),
                clean(resultSet.getString("prescriptionID")),
                clean(resultSet.getString("patientID")),
                clean(resultSet.getString("insuranceID")),
                clean(resultSet.getString("status")),
                resultSet.getObject("createdDate", java.time.LocalDate.class),
                resultSet.getObject("submittedDate", java.time.LocalDate.class),
                clean(resultSet.getString("reviewedBy")),
                resultSet.getObject("decisionDate", java.time.LocalDate.class),
                clean(resultSet.getString("decisionNotes"))
        );
    }
}
