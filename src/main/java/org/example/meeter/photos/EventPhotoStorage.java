package org.example.meeter.photos;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventPhotoStorage {
    private final Path directory;
    public EventPhotoStorage(@Value("${app.photos.events-directory:./data/photos/events}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    public Path path(String file) {
        if (file == null || !file.matches("[a-f0-9-]{36}\\.jpg")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zdjęcia.");
        return directory.resolve(file);
    }
    public String store(byte[] bytes) {
        if (bytes.length > RemotePhotoClient.MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Zdjęcie może mieć maksymalnie 4 MB.");
        String file = UUID.randomUUID() + ".jpg";
        Path destination = path(file);
        try {
            Files.createDirectories(directory);
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException("Unsupported image");
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > 16_000_000) throw new IOException("Image dimensions");
                    var image = reader.read(0);
                    double scale = Math.min(1, 1600.0 / Math.max(width, height));
                    var result = new BufferedImage(Math.max(1, (int)(width * scale)), Math.max(1, (int)(height * scale)), BufferedImage.TYPE_INT_RGB);
                    var graphics = result.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        graphics.drawImage(image, 0, 0, result.getWidth(), result.getHeight(), null);
                    } finally { graphics.dispose(); }
                    if (!ImageIO.write(result, "jpg", destination.toFile())) throw new IOException("JPEG encoder");
                } finally { reader.dispose(); }
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) remove(destination); }
            });
            return file;
        } catch (IOException | RuntimeException exception) {
            remove(destination);
            if (exception instanceof ResponseStatusException status) throw status;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Użyj zdjęcia JPG, PNG lub GIF do 4 MB i 16 megapikseli.");
        }
    }
    private void remove(Path file) {
        try { Files.deleteIfExists(file); } catch (IOException ignored) { /* Orphan cleanup can be retried later. */ }
    }
}
