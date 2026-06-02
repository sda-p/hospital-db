package com.hospitalclaims.repository;

import com.hospitalclaims.db.DatabaseConnection;
import com.hospitalclaims.model.Doctor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC repository for doctor records. */
public class DoctorRepository extends RepositorySupport implements CrudRepository<Doctor, String> {
    public DoctorRepository(DatabaseConnection databaseConnection) {
        super(databaseConnection);
    }

    @Override
    public void save(Doctor doctor) throws SQLException {
        String sql = """
                INSERT INTO Doctor (doctorID, firstname, surname, address, phone, email, specialization, hospital)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, doctor.getDoctorId());
            statement.setString(2, doctor.getFirstName());
            statement.setString(3, doctor.getSurname());
            statement.setString(4, doctor.getAddress());
            statement.setString(5, doctor.getPhone());
            statement.setString(6, doctor.getEmail());
            statement.setString(7, doctor.getSpecialization());
            statement.setString(8, doctor.getHospital());
            statement.executeUpdate();
        }
    }

    public void update(Doctor doctor) throws SQLException {
        String sql = """
                UPDATE Doctor
                SET firstname = ?, surname = ?, address = ?, phone = ?, email = ?, specialization = ?, hospital = ?
                WHERE doctorID = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, doctor.getFirstName());
            statement.setString(2, doctor.getSurname());
            statement.setString(3, doctor.getAddress());
            statement.setString(4, doctor.getPhone());
            statement.setString(5, doctor.getEmail());
            statement.setString(6, doctor.getSpecialization());
            statement.setString(7, doctor.getHospital());
            statement.setString(8, doctor.getDoctorId());
            statement.executeUpdate();
        }
    }

    public void deleteById(String doctorId) throws SQLException {
        String sql = "DELETE FROM Doctor WHERE doctorID = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, doctorId);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Doctor> findById(String doctorId) throws SQLException {
        String sql = """
                SELECT doctorID, firstname, surname, address, phone, email, specialization, hospital
                FROM Doctor
                WHERE doctorID = ?
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, doctorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Doctor> findAll() throws SQLException {
        String sql = """
                SELECT doctorID, firstname, surname, address, phone, email, specialization, hospital
                FROM Doctor
                ORDER BY surname, firstname, doctorID
                """;
        List<Doctor> doctors = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                doctors.add(mapRow(resultSet));
            }
        }
        return doctors;
    }

    public List<Doctor> findBySpecialization(String specialization) throws SQLException {
        String sql = """
                SELECT doctorID, firstname, surname, address, phone, email, specialization, hospital
                FROM Doctor
                WHERE LOWER(specialization) LIKE ?
                ORDER BY surname, firstname, doctorID
                """;
        List<Doctor> doctors = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + specialization.trim().toLowerCase() + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    doctors.add(mapRow(resultSet));
                }
            }
        }
        return doctors;
    }

    /** Maps one result-set row into the domain model. */
    private Doctor mapRow(ResultSet resultSet) throws SQLException {
        return new Doctor(
                clean(resultSet.getString("doctorID")),
                clean(resultSet.getString("firstname")),
                clean(resultSet.getString("surname")),
                clean(resultSet.getString("address")),
                clean(resultSet.getString("phone")),
                clean(resultSet.getString("email")),
                clean(resultSet.getString("specialization")),
                clean(resultSet.getString("hospital"))
        );
    }
}
