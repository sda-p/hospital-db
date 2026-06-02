package com.hospitalclaims.repository;

import com.hospitalclaims.db.DatabaseConnection;
import com.hospitalclaims.model.Drug;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC repository for drug catalogue entries. */
public class DrugRepository extends RepositorySupport implements CrudRepository<Drug, String> {
    public DrugRepository(DatabaseConnection databaseConnection) {
        super(databaseConnection);
    }

    @Override
    public void save(Drug drug) throws SQLException {
        String sql = "INSERT INTO Drug (drugID, drugname, sideeffects, purpose) VALUES (?, ?, ?, ?)";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, drug.getDrugId());
            statement.setString(2, drug.getDrugName());
            statement.setString(3, drug.getSideEffects());
            statement.setString(4, drug.getPurpose());
            statement.executeUpdate();
        }
    }

    public void update(Drug drug) throws SQLException {
        String sql = "UPDATE Drug SET drugname = ?, sideeffects = ?, purpose = ? WHERE drugID = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, drug.getDrugName());
            statement.setString(2, drug.getSideEffects());
            statement.setString(3, drug.getPurpose());
            statement.setString(4, drug.getDrugId());
            statement.executeUpdate();
        }
    }

    public void deleteById(String drugId) throws SQLException {
        String sql = "DELETE FROM Drug WHERE drugID = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, drugId);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Drug> findById(String drugId) throws SQLException {
        String sql = "SELECT drugID, drugname, sideeffects, purpose FROM Drug WHERE drugID = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, drugId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Drug> findAll() throws SQLException {
        String sql = "SELECT drugID, drugname, sideeffects, purpose FROM Drug ORDER BY drugname, drugID";
        List<Drug> drugs = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                drugs.add(mapRow(resultSet));
            }
        }
        return drugs;
    }

    public List<Drug> findByName(String drugName) throws SQLException {
        String sql = """
                SELECT drugID, drugname, sideeffects, purpose
                FROM Drug
                WHERE LOWER(drugname) LIKE ?
                ORDER BY drugname, drugID
                """;
        List<Drug> drugs = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + drugName.trim().toLowerCase() + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    drugs.add(mapRow(resultSet));
                }
            }
        }
        return drugs;
    }

    /** Maps one result-set row into the domain model. */
    private Drug mapRow(ResultSet resultSet) throws SQLException {
        return new Drug(
                clean(resultSet.getString("drugID")),
                clean(resultSet.getString("drugname")),
                clean(resultSet.getString("sideeffects")),
                clean(resultSet.getString("purpose"))
        );
    }
}
