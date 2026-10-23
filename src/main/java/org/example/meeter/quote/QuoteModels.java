package org.example.meeter.quote;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;

public final class QuoteModels {
    private QuoteModels() {}
    public record Input(@NotBlank @Size(min = 4, max = 10240) String quote, @NotNull(message = "Wybierz autora cytatu.") @Positive Long authorId, Boolean isPublic) {}
    public record AuthorView(Long id, String fullName, String profilePhotoUrl) {}
    public record View(Long id, UUID uuid, String quote, AuthorView author, boolean isPublic, LocalDateTime addTime) {
        static View of(Quote quote) {
            var author = quote.getAuthor();
            return new View(quote.getId(), quote.getUuid(), quote.getQuote(),
                author == null ? null : new AuthorView(author.getId(), author.getFullName(), author.getProfilePhotoUrl()),
                quote.isPublic(), quote.getAddTime());
        }
    }
}
