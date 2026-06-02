package com.hospitalclaims.repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Minimal CRUD contract shared by the JDBC repositories. */
public interface CrudRepository<T, ID> {
    /** Inserts a new row for the supplied entity. */
    void save(T entity) throws SQLException;

    /** Finds one entity by its primary business key. */
    Optional<T> findById(ID id) throws SQLException;

    /** Returns every entity in the repository's default display order. */
    List<T> findAll() throws SQLException;
}
