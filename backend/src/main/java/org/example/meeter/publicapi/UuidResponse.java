package org.example.meeter.publicapi;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.function.BiFunction;
import org.example.meeter.*;
import org.example.meeter.auth.*;
import org.example.meeter.interactions.*;
import org.example.meeter.news.NewsController;
import org.example.meeter.quote.QuoteModels;
import org.example.meeter.quote.game.*;
import org.example.meeter.superpowers.SuperpowerController;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import static org.example.meeter.publicapi.PublicIdentifiers.Kind;

/** The browser contract contains UUIDs, including nested DTO references and image URLs. */
@ControllerAdvice
public class UuidResponse implements ResponseBodyAdvice<Object> {
    private final PublicIdentifiers identifiers;
    private final tools.jackson.databind.json.JsonMapper json;
    public UuidResponse(PublicIdentifiers identifiers, tools.jackson.databind.json.JsonMapper json) {
        this.identifiers = identifiers; this.json = json;
    }
    private static final Map<Class<?>, Kind> PRIMARY = Map.ofEntries(
        Map.entry(ApiModels.HumanView.class, Kind.HUMAN), Map.entry(InteractionModels.PersonView.class, Kind.HUMAN),
        Map.entry(QuoteModels.AuthorView.class, Kind.HUMAN), Map.entry(QuoteGameModels.Person.class, Kind.HUMAN),
        Map.entry(ApiModels.PlaceView.class, Kind.PLACE), Map.entry(InteractionService.MapPoint.class, Kind.PLACE),
        Map.entry(ApiModels.CliqueView.class, Kind.CLIQUE), Map.entry(CliqueDirectory.Card.class, Kind.CLIQUE),
        Map.entry(InteractionModels.View.class, Kind.INTERACTION), Map.entry(QuoteGameVisits.Option.class, Kind.INTERACTION),
        Map.entry(ApiModels.PublicQuoteView.class, Kind.QUOTE), Map.entry(QuoteModels.View.class, Kind.QUOTE),
        Map.entry(QuoteModels.RankedView.class, Kind.QUOTE), Map.entry(QuoteGameModels.Summary.class, Kind.GAME),
        Map.entry(CurrentAccount.Principal.class, Kind.USER), Map.entry(AdminUsers.Created.class, Kind.USER),
        Map.entry(NewsController.Item.class, Kind.NEWS), Map.entry(SuperpowerController.View.class, Kind.SUPERPOWER),
        Map.entry(org.example.meeter.activity.PhysicalActivityModels.View.class, Kind.ACTIVITY)
    );
    private static final Pattern PHOTO = Pattern.compile("^/api/(people(?:/(?:browse|participants))?|events|cliques|superpowers)/(\\d+)(/photo)(.*)$");

    @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servlet)
                || !Boolean.TRUE.equals(servlet.getServletRequest().getAttribute(UuidApiFilter.ATTRIBUTE))) return body;
        if (body == null || body instanceof org.springframework.core.io.Resource) return body;
        if (!mediaType.isCompatibleWith(MediaType.APPLICATION_JSON) && !mediaType.getSubtype().endsWith("+json")) return body;
        Map<Kind, Set<Long>> requested = new EnumMap<>(Kind.class);
        walk(body, (kind, id) -> { requested.computeIfAbsent(kind, ignored -> new HashSet<>()).add(id); return id; });
        var values = identifiers.external(requested);
        // A JSON tree also prevents the message converter reusing a List<InternalDto>
        // element serializer for the projected maps. Use Boot's mapper for date conventions.
        return json.valueToTree(walk(body, (kind, id) -> values.get(kind).get(id)));
    }
    private static Object walk(Object value, BiFunction<Kind, Long, Object> ids) {
        if (value == null) return null;
        if (value instanceof Collection<?> list) return list.stream().map(item -> walk(item, ids)).toList();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put((String) key, field((String) key, null, item, ids)));
            return result;
        }
        if (!value.getClass().isRecord()) return value;
        Map<String, Object> result = new LinkedHashMap<>();
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            // Import diagnostics can reference an absent clique parsed from a filename.
            // The browser uses the filename/status, and does not need that internal ID.
            if (value instanceof org.example.meeter.photos.CliquePhotoImport.Item
                    && component.getName().equals("cliqueId")) continue;
            try {
                Object item = component.getAccessor().invoke(value);
                result.put(component.getName(), field(component.getName(), PRIMARY.get(value.getClass()), item, ids));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot serialize public DTO", failure); }
        }
        return result;
    }
    private static Object field(String name, Kind primary, Object value, BiFunction<Kind, Long, Object> ids) {
        Kind kind = name.equals("id") ? primary : PublicIdentifiers.field(name);
        if (value instanceof Number id && (name.equals("id") || kind != null)) {
            if (kind == null) throw new IllegalStateException("Unmapped public DTO identifier");
            return ids.apply(kind, id.longValue());
        }
        if (kind != null && value instanceof Collection<?> list)
            return list.stream().map(item -> item == null ? null : ids.apply(kind, ((Number) item).longValue())).toList();
        if ((name.equals("profilePhotoUrl") || name.equals("photoUrl")) && value instanceof String url) {
            if (url.startsWith("/api/v2/")) return url;
            var photo = PHOTO.matcher(url);
            if (photo.matches()) {
                Kind photoKind = photo.group(1).startsWith("people") ? Kind.HUMAN
                    : photo.group(1).equals("events") ? Kind.INTERACTION
                    : photo.group(1).equals("superpowers") ? Kind.SUPERPOWER : Kind.CLIQUE;
                Object uuid = ids.apply(photoKind, Long.valueOf(photo.group(2)));
                return "/api/v2/" + photo.group(1) + "/" + uuid + photo.group(3) + photo.group(4);
            }
            return url.startsWith("/api/") ? "/api/v2/" + url.substring(5) : url;
        }
        return walk(value, ids);
    }
}
