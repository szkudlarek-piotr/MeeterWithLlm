package org.example.meeter.photos;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.example.meeter.Human;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhotoStorage {
    private final RemotePhotoClient client;
    private final Path directory;
    private final Path formerDirectory;
    private final org.example.meeter.multimedia.photo.PhotoRepository photos;
    public PhotoStorage(RemotePhotoClient client, @Value("${app.photos.directory:./data/photos/people}") String directory,
                        org.example.meeter.multimedia.photo.PhotoRepository photos,
                        @Value("${app.photos.former-directory:./data/photos/former-people}") String formerDirectory) {
        this.client = client; this.directory = Path.of(directory).toAbsolutePath().normalize(); this.photos=photos;
        this.formerDirectory = Path.of(formerDirectory).toAbsolutePath().normalize();
    }

    public void importPhoto(Human human, String url) {
        importBytes(human,client.download(PhotoUrlPolicy.validate(url)));
    }

    public void importBytes(Human human, byte[] bytes) {
        if (bytes.length > RemotePhotoClient.MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Zdjęcie może mieć maksymalnie 4 MB.");
        String file = human.getUuid() + "-" + UUID.randomUUID() + ".jpg";
        Path destination = path(file);
        try {
            Files.createDirectories(directory);
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw badImage();
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > 16_000_000) throw badImage();
                    BufferedImage image = reader.read(0);
                    BufferedImage avatar = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = avatar.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 512, 512);
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        double factor = 512.0 / Math.min(width, height);
                        int scaledWidth = (int) Math.ceil(width * factor), scaledHeight = (int) Math.ceil(height * factor);
                        graphics.drawImage(image, (512 - scaledWidth) / 2, (512 - scaledHeight) / 2, scaledWidth, scaledHeight, null);
                    } finally { graphics.dispose(); }
                    if (!ImageIO.write(avatar, "jpg", destination.toFile())) throw new IOException("Brak kodera JPEG.");
                } finally { reader.dispose(); }
            }
            archivePrevious(human);
            human.setProfilePhotoFile(file);
            var photo = new org.example.meeter.multimedia.photo.Photo();
            photo.setPhotoName(file);
            photo.setPhotoType(org.example.meeter.multimedia.photo.PhotoType.PROFILE_PICTURE);
            photo.setGenerationTime(java.time.LocalDateTime.now());
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) remove(file);
                    }
                });
            }
            human.setProfilePhoto(photos.save(photo));
        } catch (IOException exception) {
            remove(file);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie można odczytać zdjęcia. Użyj pliku JPG, PNG lub GIF do 16 megapikseli.");
        } catch (RuntimeException exception) {
            remove(file);
            throw exception;
        }
    }

    private void archivePrevious(Human human) throws IOException {
        var previous = human.getProfilePhoto();
        if (previous == null && human.getProfilePhotoFile() == null) return;
        if (previous != null && previous.getPhotoType() != org.example.meeter.multimedia.photo.PhotoType.PROFILE_PICTURE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Poprzednie zdjęcie ma nieprawidłowy typ.");
        Path source = previous == null ? path(human.getProfilePhotoFile()) : importedPath(previous.getPhotoName());
        Files.createDirectories(formerDirectory);
        if (formerDirectory.toRealPath().equals(directory.toRealPath())) throw new IOException("Archiwum wymaga osobnego folderu.");
        String suffix = source.getFileName().toString().replaceFirst("^.*\\.", "").toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("jpg", "jpeg", "png", "gif").contains(suffix)) suffix = "img";
        String archivedName = UUID.randomUUID() + "." + suffix;
        Path archived = formerDirectory.resolve(archivedName);
        // Copy before commit; retain the active file until the database commit succeeds.
        Files.copy(source, archived);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                try {
                    if (status == STATUS_COMMITTED) Files.deleteIfExists(source);
                    else Files.deleteIfExists(archived);
                } catch (IOException ignored) { /* Retain an extra copy rather than lose a photo. */ }
            }
        });
        if (previous == null) {
            previous = new org.example.meeter.multimedia.photo.Photo();
            // The historical creation date is unknown; do not invent it.
        }
        previous.setPhotoName(archivedName);
        previous.setPhotoType(org.example.meeter.multimedia.photo.PhotoType.FORMER_HUMAN_PHOTO);
        photos.save(previous);
    }
    public Path path(String filename) {
        if (filename == null || !filename.matches("[a-f0-9-]{73}\\.jpg"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zdjęcia.");
        Path result = directory.resolve(filename).normalize();
        if (!result.startsWith(directory)) throw new IllegalArgumentException("Nieprawidłowa ścieżka zdjęcia.");
        return result;
    }
    /** Original photo names are filenames, never URLs or paths. Do not follow symlinks outside storage. */
    public Path importedPath(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.contains("/") || filename.contains("\\") || filename.contains(":")
                || filename.equals(".") || filename.equals(".."))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zdjęcia.");
        try {
            Path root = directory.toRealPath();
            Path result = root.resolve(filename).toRealPath();
            if (!result.startsWith(root) || !Files.isRegularFile(result)) throw new IOException();
            return result;
        } catch (IOException | InvalidPathException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zdjęcia.");
        }
    }
    private void remove(String filename) {
        try { Files.deleteIfExists(path(filename)); } catch (IOException ignored) { /* Można usunąć osierocony plik później. */ }
    }
    private static ResponseStatusException badImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plik nie jest obsługiwanym zdjęciem JPG/PNG/GIF lub ma więcej niż 16 megapikseli.");
    }
}
