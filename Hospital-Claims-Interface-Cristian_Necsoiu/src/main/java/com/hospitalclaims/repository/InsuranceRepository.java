package com.hospitalclaims.repository;

import com.hospitalclaims.db.DatabaseConnection;
import com.hospitalclaims.model.Insurance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC repository for insurance providers. */
public class InsuranceRepository extends RepositorySupport implements CrudRepository<Insurance, String> {
    public InsuranceRepository(DatabaseConnection databaseConnection) {
        super(databaseConnection);
    }

    @Override
    public void save(Insurance insurance) throws SQLException {
        String sql = "INSERT INTO Insurance (insuranceID, company, address, phone) VALUES (?, ?, ?, ?)";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, insurance.getInsuranceId());
            statement.setString(2, insurance.getCompany());
            statement.setString(3, insurance.getAddress());
            statement.setString(4, insurance.getPhone());
            statement.executeUpdate();
        }
    }

    public void update(Insurance insurance) throws SQLException {
        String sql = "UPDATE Insurance SET company = ?, address = ?, phone = ? WHERE insuranceID = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, insurance.getCompany());
            statement.setString(2, insurance.getAddress());
            statement.setString(3, insurance.getPhone());
            statement.setString(4, insurance.getInsuranceId());
            statement.executeUpdate();
        }
    }

    public void deleteById(String insuranceId) throws SQLException {
        String sql = "DELETE FROM Insurance WHERE insuranceID = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, insuranceId);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Insurance> findById(String insuranceId) throws SQLException {
        String sql = "SELECT insuranceID, company, address, phone FROM Insurance WHERE insuranceID = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, insuranceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Insurance> findAll() throws SQLException {
        String sql = "SELECT insuranceID, company, address, phone FROM Insurance ORDER BY company";
        List<Insurance> providers = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                providers.add(mapRow(resultSet));
            }
        }
        return providers;
    }

    /** Maps one result-set row into the domain model. */
    private Insurance mapRow(ResultSet resultSet) throws SQLException {
        return new Insurance(
                clean(resultSet.getString("insuranceID")),
                clean(resultSet.getString("company")),
                clean(resultSet.getString("address")),
                clean(resultSet.getString("phone"))
        );
    }
}
