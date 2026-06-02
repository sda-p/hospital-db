package com.hospitalclaims.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/** File-backed storage for saved browser search definitions. */
public final class SavedViewSearchStore {
    private static final String DELIMITER = "\t";
    private final Path storagePath;

    public SavedViewSearchStore(Path storagePath) {
        this.storagePath = storagePath;
    }

    /** Returns every saved search sorted for predictable UI rendering. */
    public synchronized List<SavedViewSearch> findAll() {
        return load().stream()
                .sorted(Comparator.comparing(SavedViewSearch::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Looks up one saved search by name using case-insensitive matching. */
    public synchronized SavedViewSearch findByName(String name) {
        return load().stream()
                .filter(search -> search.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new SearchQueryException("Unknown saved search: " + name + "."));
    }

    /** Replaces any existing saved search with the same logical name. */
    public synchronized void save(SavedViewSearch search) {
        if (search.name() == null || search.name().isBlank()) {
            throw new SearchQueryException("Saved search name must not be blank.");
        }
        String normalizedName = search.name().trim();
        if (!normalizedName.matches("[A-Za-z0-9 _-]{1,40}")) {
            throw new SearchQueryException("Saved search name must use letters, numbers, spaces, hyphens, or underscores.");
        }

        List<SavedViewSearch> existing = new ArrayList<>(load());
        existing.removeIf(item -> item.name().equalsIgnoreCase(normalizedName));
        existing.add(new SavedViewSearch(
                normalizedName,
                search.dataset(),
                safe(search.query()),
                safe(search.sort()),
                safe(search.group()),
                search.groupSort(),
                search.pageSize()
        ));
        store(existing);
    }

    /** Deletes a saved search by name. */
    public synchronized void delete(String name) {
        if (name == null || name.isBlank()) {
            throw new SearchQueryException("Saved search name must not be blank.");
        }
        List<SavedViewSearch> existing = new ArrayList<>(load());
        boolean removed = existing.removeIf(item -> item.name().equalsIgnoreCase(name.trim()));
        if (!removed) {
            throw new SearchQueryException("Unknown saved search: " + name + ".");
        }
        store(existing);
    }

    private List<SavedViewSearch> load() {
        Properties properties = new Properties();
        if (Files.exists(storagePath)) {
            try (InputStream inputStream = Files.newInputStream(storagePath)) {
                properties.load(inputStream);
            } catch (IOException exception) {
                throw new SearchQueryException("Could not read saved searches.");
            }
        }

        List<SavedViewSearch> searches = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            searches.add(deserialize(key, properties.getProperty(key)));
        }
        return searches;
    }

    /** Rewrites the backing properties file from the current in-memory list. */
    private void store(List<SavedViewSearch> searches) {
        Properties properties = new Properties();
        for (SavedViewSearch search : searches) {
            properties.setProperty(search.name(), serialize(search));
        }
        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream outputStream = Files.newOutputStream(storagePath)) {
                properties.store(outputStream, "Saved view searches");
            }
        } catch (IOException exception) {
            throw new SearchQueryException("Could not write saved searches.");
        }
    }

    private String serialize(SavedViewSearch search) {
        return encode(search.dataset())
                + DELIMITER + encode(search.query())
                + DELIMITER + encode(search.sort())
                + DELIMITER + encode(search.group())
                + DELIMITER + search.groupSort()
                + DELIMITER + search.pageSize();
    }

    private SavedViewSearch deserialize(String name, String value) {
        String[] parts = value == null ? new String[0] : value.split(DELIMITER, -1);
        if (parts.length != 6) {
            throw new SearchQueryException("Saved search data is invalid for " + name + ".");
        }
        return new SavedViewSearch(
                name,
                decode(parts[0]),
                decode(parts[1]),
                decode(parts[2]),
                decode(parts[3]),
                Boolean.parseBoolean(parts[4]),
                Integer.parseInt(parts[5])
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
