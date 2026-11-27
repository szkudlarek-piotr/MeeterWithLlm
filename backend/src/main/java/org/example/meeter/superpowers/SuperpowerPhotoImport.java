package org.example.meeter.superpowers;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.example.meeter.auth.CurrentAccount;
import org.example.meeter.multimedia.photo.*;
import org.example.meeter.photos.RemotePhotoClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Imports superpower photos from a server folder of files named ID.jpg, ID.jpeg, ID.png or ID.gif. */
@Service
public class SuperpowerPhotoImport {
    private static final Pattern FILENAME = Pattern.compile("([1-9][0-9]*)\\.(jpg|jpeg|png|gif)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_FILES = 5000;
    private final CurrentAccount account;
    private final SuperpowerRepository superpowers;
    private final PhotoRepository photos;
    private final SuperpowerPhotoStorage storage;
    private final TransactionTemplate transaction;

    public SuperpowerPhotoImport(CurrentAccount account, SuperpowerRepository superpowers, PhotoRepository photos,
            SuperpowerPhotoStorage storage, PlatformTransactionManager manager) {
        this.account = account; this.superpowers = superpowers; this.photos = photos; this.storage = storage;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** status is one of IMPORTED, WOULD_IMPORT (dry run), SKIPPED or FAILED. */
    public record Item(String filename, Long superpowerId, String superpowerName, String status, String message) {}
    public record Report(boolean dryRun, long imported, long wouldImport, long skipped, long failed, List<Item> items) {}

    public Report importFolder(String folder, boolean dryRun) {
        if (!account.isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Wymagane uprawnienia administratora.");
        final Path root;
        final List<Path> files;
        try {
            root = Path.of(folder).toRealPath();
            if (!Files.isDirectory(root)) throw new IOException();
            try (var entries = Files.list(root)) { files = entries.sorted().limit(MAX_FILES + 1L).toList(); }
            if (files.size() > MAX_FILES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder może zawierać maksymalnie 5000 plików. Podziel import na mniejsze foldery.");
        } catch (IOException | InvalidPathException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder nie istnieje lub backend nie ma dostępu do jego odczytu.");
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (Path file : files) {
            Long id = superpowerId(file);
            if (id != null) counts.merge(id, 1, Integer::sum);
        }
        var items = new ArrayList<Item>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            Long id = superpowerId(file);
            if (id == null) { items.add(new Item(name, null, null, "SKIPPED", "Oczekiwana nazwa: ID.jpg, ID.png lub ID.gif.")); continue; }
            if (counts.get(id) > 1) { items.add(new Item(name, id, null, "FAILED", "Kilka plików dla tej samej supermocy. Pozostaw jeden.")); continue; }
            try {
                items.add(dryRun
                    ? transaction.execute(status -> preview(root, file, id))
                    : transaction.execute(status -> importFile(root, file, id)));
            } catch (Exception failure) {
                // Never return filesystem paths, database messages or exception details.
                items.add(new Item(name, id, null, "FAILED", "Nie udało się zapisać zdjęcia. Użyj JPG, PNG lub GIF do 4 MB i 16 megapikseli."));
            }
        }
        return new Report(dryRun,
            count(items, "IMPORTED"), count(items, "WOULD_IMPORT"), count(items, "SKIPPED"), count(items, "FAILED"), items);
    }

    private Item preview(Path root, Path file, Long id) {
        String name = file.getFileName().toString();
        var superpower = superpowers.findById(id).orElse(null);
        if (superpower == null) return new Item(name, id, null, "SKIPPED", "Nie ma supermocy o tym ID.");
        if (superpower.getSuperpowerPhoto() != null) return new Item(name, id, superpower.getSuperpowerName(), "SKIPPED", "Supermoc ma już zdjęcie.");
        readSource(root, file);
        return new Item(name, id, superpower.getSuperpowerName(), "WOULD_IMPORT", "Zdjęcie zostanie zapisane.");
    }

    private Item importFile(Path root, Path file, Long id) {
        String name = file.getFileName().toString();
        var superpower = superpowers.lockForPhoto(id).orElse(null);
        if (superpower == null) return new Item(name, id, null, "SKIPPED", "Nie ma supermocy o tym ID.");
        if (superpower.getSuperpowerPhoto() != null) return new Item(name, id, superpower.getSuperpowerName(), "SKIPPED", "Supermoc ma już zdjęcie.");
        byte[] bytes = readSource(root, file);
        var photo = new Photo();
        photo.setPhotoType(PhotoType.SUPERPOWER);
        photo.setGenerationTime(LocalDateTime.now());
        photo = photos.save(photo);
        // The stored file takes its name from the photo UUID: {uuid_zdjecia}.jpg.
        photo.setPhotoName(storage.storeAs(bytes, photo.getUuid() + ".jpg"));
        superpower.setSuperpowerPhoto(photo);
        superpowers.flush();
        return new Item(name, id, superpower.getSuperpowerName(), "IMPORTED", "Zaimportowano zdjęcie.");
    }

    private static byte[] readSource(Path root, Path file) {
        try {
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !file.toRealPath().startsWith(root)) throw new IOException();
            try (var input = Files.newInputStream(file)) {
                byte[] bytes = input.readNBytes(RemotePhotoClient.MAX_BYTES + 1);
                if (bytes.length > RemotePhotoClient.MAX_BYTES) throw new IOException();
                return bytes;
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Source image unavailable", failure);
        }
    }

    private static long count(List<Item> items, String status) {
        return items.stream().filter(item -> item.status().equals(status)).count();
    }

    private static Long superpowerId(Path file) {
        var matcher = FILENAME.matcher(file.getFileName().toString());
        if (!matcher.matches()) return null;
        try { return Long.valueOf(matcher.group(1)); } catch (NumberFormatException ignored) { return null; }
    }
}
