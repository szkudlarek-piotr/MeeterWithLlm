package org.example.meeter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.*;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.example.meeter.auth.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.example.meeter.interactions.InteractionRepository;
import org.example.meeter.superpowers.Superpower;
import tools.jackson.databind.json.JsonMapper;
import org.example.meeter.photos.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:api-tests;DB_CLOSE_DELAY=-1",
    "app.dev.bootstrap=false",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@org.springframework.context.annotation.Import(ApiIntegrationTests.ErrorTestController.class)
class ApiIntegrationTests {
    @org.springframework.web.bind.annotation.RestController
    static class ErrorTestController {
        @org.springframework.web.bind.annotation.GetMapping("/api/test/server-error")
        public String failure() { throw new IllegalStateException("Synthetic server failure"); }
    }
    @LocalServerPort int port;
    @Autowired HumanRepository people;
    @Autowired org.example.meeter.multimedia.photo.PhotoRepository photoRecords;
    @Autowired CliqueRepository cliques;
    @Autowired PlaceRepository places;
    @Autowired UserRepository users;
    @Autowired TokenRepository tokens;
    @Autowired AuthEventRepository authEvents;
    @Autowired org.example.meeter.news.NewsRepository news;
    @Autowired org.example.meeter.superpowers.SuperpowerRepository superpowers;
    @Autowired org.example.meeter.superpowers.HumanSuperpowerRepository humanSuperpower;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired InteractionRepository interactions;
    @Autowired org.example.meeter.quote.QuoteRepository quotes;
    @Autowired org.example.meeter.quote.game.QuoteGameRepository quoteGames;
    private final JsonMapper json = JsonMapper.builder().build();
    private HttpClient client;
    private String csrf;
    private String username;
    private User owner;
    private String loginCookie;
    @MockitoBean RemotePhotoClient remotePhotos;
    @MockitoBean org.example.meeter.auth.RegistrationMail registrationMail;
    @MockitoBean org.example.meeter.recommendations.ParticipantModelClient participantModels;
    private static final Path PHOTO_DIRECTORY=createPhotoDirectory();
    private static final Path CREDENTIAL_FILE=PHOTO_DIRECTORY.resolve("credentials/passwords.csv");
    private static Path createPhotoDirectory() {
        try { return Files.createTempDirectory("meeter-photo-tests-"); }
        catch(IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }
    @DynamicPropertySource static void photos(DynamicPropertyRegistry properties) {
        properties.add("MEETER_INVITATION_JWT_SECRET", () -> "test-only-meeter-invitation-signing-secret");
        properties.add("app.admin.passwords-file",()->CREDENTIAL_FILE.toString());
        properties.add("app.photos.cliques-directory",()->PHOTO_DIRECTORY.resolve("cliques").toString());
        properties.add("app.photos.directory",()->PHOTO_DIRECTORY.toString());
        properties.add("app.photos.former-directory",()->PHOTO_DIRECTORY.resolve("former").toString());
        properties.add("app.photos.events-directory",()->PHOTO_DIRECTORY.resolve("events").toString());
        properties.add("app.photos.place-categories-directory",()->PHOTO_DIRECTORY.resolve("categories").toString());
    }

    @Test void invitationActivationLinksExistingPersonAndConsumesToken() throws Exception {
        String login = "new-registration";
        String password = "registration-password";
        String email = "anna@example.test";
        var invited = new Human("Anna", "Testowa", null, null, null, null, null, null);
        invited.assignOwner(owner);
        invited = people.save(invited);
        long personCount = people.count();
        var issue = request("POST", "/v2/auth/invitations/" + invited.getUuid(), "{}");
        assertEquals(200, issue.statusCode(), issue.body());
        String token = json.readTree(issue.body()).get("token").asString();
        assertEquals(3, token.split("\\.").length);
        var browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String base = "http://127.0.0.1:" + port + "/api/v2/auth";
        var preparation = browser.send(HttpRequest.newBuilder(URI.create(base + "/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        var csrfToken = json.readTree(preparation.body()).get("token").asText();
        var activate = HttpRequest.newBuilder(URI.create(base + "/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(java.util.Map.of(
                "token", token, "username", login, "email", email, "password", password))));
        assertEquals(403, browser.send(activate.build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        assertFalse(users.existsByUsernameIgnoreCase(login));
        activate.header("X-XSRF-TOKEN", csrfToken);
        var tampered = HttpRequest.newBuilder(URI.create(base + "/register"))
            .header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrfToken)
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(java.util.Map.of(
                "token", token + "A", "username", login, "email", email, "password", password))));
        assertEquals(400, browser.send(tampered.build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(204, browser.send(activate.build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        assertFalse(users.existsByUsernameIgnoreCase(login));
        var verificationToken = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(registrationMail).send(org.mockito.Mockito.eq(email), verificationToken.capture());
        var verification = HttpRequest.newBuilder(URI.create(base + "/verify-email"))
            .header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrfToken)
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(java.util.Map.of(
                "token", verificationToken.getValue())))).build();
        assertEquals(204, browser.send(verification, HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(400, browser.send(verification, HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(400, browser.send(activate.build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        var registered = users.findByUsername(login).orElseThrow();
        assertEquals(java.util.List.of("ROLE_USER"), jdbc.queryForList(
            "select role from user_roles where user_id = ?", String.class, registered.getId()));
        assertEquals("Anna Testowa", registered.getHuman().getFullName());
        assertEquals(invited.getId(), registered.getHuman().getId());
        assertEquals(email, registered.getEmail());
        assertEquals(owner.getId(), jdbc.queryForObject(
            "select inviter_user_id from account_invitation_history where invited_user_id = ?", Long.class, registered.getId()));
        assertEquals(invited.getId(), jdbc.queryForObject(
            "select invited_human_id from account_invitation_history where invited_user_id = ?", Long.class, registered.getId()));
        assertEquals(personCount, people.count());
        assertTrue(encoder.matches(password, registered.getPasswordHash()));
    }

    @Test void invitationsRejectUnownedPeopleAndRegistrationRequiresToken() throws Exception {
        var unowned = people.save(new Human("Bez", "Właściciela", null, null, null, null, null, null));
        assertEquals(404, request("POST", "/v2/auth/invitations/" + unowned.getUuid(),
            "{\"username\":\"invited\",\"email\":\"invited@example.com\"}").statusCode());
        assertEquals(400, request("POST", "/v2/auth/register", "{}").statusCode());
    }

    @BeforeEach void clean() throws Exception {
        jdbc.update("delete from account_invitation_history");
        jdbc.update("delete from pending_registrations");
        Files.deleteIfExists(CREDENTIAL_FILE);
        humanSuperpower.deleteAll();
        superpowers.deleteAll();
        news.deleteAll();
        quoteGames.deleteAll();
        quotes.deleteAll();
        interactions.deleteAll();
        tokens.deleteAll();
        authEvents.deleteAll();
        for (String table : new String[] {"people", "places", "cliques"}) jdbc.update("update " + table + " set created_by_id=null");
        users.deleteAll(); people.deleteAll(); jdbc.update("update cliques set photo_id=null"); photoRecords.deleteAll(); cliques.deleteAll(); places.deleteAll();
        Human human = people.save(new Human("Test", "Owner", null, null, null, null, null, null));
        username = "test-" + UUID.randomUUID();
        owner = users.save(new User(username, encoder.encode("test-password-123456"), human));
        human.assignOwner(owner); people.save(human);
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var tokenResponse = request("GET", "/auth/csrf", null);
        var matcher = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(tokenResponse.body());
        assertTrue(matcher.find(), tokenResponse.body()); csrf = matcher.group(1);
        var login = request("POST", "/auth/login", "{\"username\":\"" + username + "\",\"password\":\"test-password-123456\"}");
        assertEquals(200, login.statusCode(), login.body());
        loginCookie = login.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
    }

    @Test void uuidAuthenticationPreservesAnonymousLoginCsrfAndLogout() throws Exception {
        var browser=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String base="http://127.0.0.1:"+port+"/api/v2";
        assertEquals(401,browser.send(HttpRequest.newBuilder(URI.create(base+"/auth/me")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        var preparation=browser.send(HttpRequest.newBuilder(URI.create(base+"/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,preparation.statusCode(),preparation.body());
        String token=json.readTree(preparation.body()).get("token").asString();
        String payload="{\"username\":\""+username+"\",\"password\":\"test-password-123456\"}";
        assertEquals(403,browser.send(HttpRequest.newBuilder(URI.create(base+"/auth/login")).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        var login=browser.send(HttpRequest.newBuilder(URI.create(base+"/auth/login")).header("Content-Type","application/json").header("X-XSRF-TOKEN",token)
            .POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,login.statusCode(),login.body());
        assertEquals(owner.getUuid().toString(),json.readTree(login.body()).get("id").asString());
        assertEquals(204,browser.send(HttpRequest.newBuilder(URI.create(base+"/auth/logout")).header("Content-Type","application/json").header("X-XSRF-TOKEN",token)
            .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401,browser.send(HttpRequest.newBuilder(URI.create(base+"/auth/me")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test void uuidBrowserContractCoversFormsPathsPermissionsAndPortraits() throws Exception {
        var me=json.readTree(request("GET","/v2/auth/me",null).body());
        assertEquals(owner.getUuid().toString(),me.get("id").asString());
        assertEquals(owner.getHuman().getUuid().toString(),me.get("humanId").asString());
        assertEquals(200,request("GET","/v2/auth/csrf",null).statusCode());
        var placeResponse=request("POST","/v2/places","{\"name\":\"UUID place\",\"latitude\":52,\"longitude\":21}");
        assertEquals(201,placeResponse.statusCode(),placeResponse.body());
        String place=json.readTree(placeResponse.body()).get("id").asString(); UUID.fromString(place);
        var cliqueResponse=request("POST","/v2/cliques","{\"cliqueName\":\"UUID clique\",\"cliqueCapitalId\":\""+place+"\"}");
        assertEquals(201,cliqueResponse.statusCode(),cliqueResponse.body());
        String clique=json.readTree(cliqueResponse.body()).get("id").asString(); UUID.fromString(clique);
        var personResponse=request("POST","/v2/people","{\"name\":\"UUID person\",\"homeId\":\""+place+"\",\"cliqueId\":\""+clique+"\"}");
        assertEquals(201,personResponse.statusCode(),personResponse.body());
        var person=json.readTree(personResponse.body()); String human=person.get("id").asString();
        assertEquals(human,person.get("uuid").asString());
        assertEquals(place,person.get("home").get("id").asString());
        assertEquals(clique,person.get("clique").get("id").asString());
        String common="\"details\":{\"name\":\"UUID interaction\",\"participantIds\":[\""+human+"\"]}";
        var bodies=java.util.Map.of(
            "meetings","{"+common+",\"date\":\"2026-10-01\",\"placeId\":\""+place+"\"}",
            "visits","{"+common+",\"visitDate\":\"2026-10-01T12:00\",\"duration\":1,\"placeId\":\""+place+"\"}",
            "events","{"+common+",\"startDate\":\"2026-10-01T12:00\",\"stopDate\":\"2026-10-01T20:00\",\"placeId\":\""+place+"\"}",
            "trips","{"+common+",\"startDate\":\"2026-10-01T12:00\",\"endDate\":\"2026-10-03T12:00\",\"placeIds\":[\""+place+"\"]}",
            "flatmates","{"+common+",\"humanId\":\""+human+"\",\"startDate\":\"2026-10-01\"}",
            "weddings","{"+common+",\"dateTime\":\"2026-10-01T12:00\",\"groomId\":\""+me.get("humanId").asString()+"\",\"brideId\":\""+human+"\",\"ceremonyPlaceId\":\""+place+"\",\"hadAfter\":false,\"wasIInvited\":true}");
        for(var item:bodies.entrySet()) {
            var created=request("POST","/v2/"+item.getKey(),item.getValue());
            assertEquals(201,created.statusCode(),created.body());
            var value=json.readTree(created.body()); String interaction=value.get("id").asString(); UUID.fromString(interaction);
            assertEquals(interaction,value.get("uuid").asString());
            for(var member:value.get("participants")) UUID.fromString(member.get("id").asString());
            for(var member:value.get("removableParticipantIds")) UUID.fromString(member.asString());
            assertEquals(200,request("GET","/v2/interactions/"+interaction,null).statusCode());
            assertEquals(200,request("PUT","/v2/"+item.getKey()+"/"+interaction,
                item.getValue().replace("\"participantIds\":[\""+human+"\"]","\"participantIds\":[]")).statusCode());
        }
        var profile=request("GET","/v2/people/browse/"+human,null); assertEquals(200,profile.statusCode(),profile.body());
        var mapResponse=request("GET","/v2/people/browse/"+human+"/map",null);
        assertEquals(200,mapResponse.statusCode(),mapResponse.body());
        var groupResponse=request("GET","/v2/cliques/browse",null);
        assertEquals(200,groupResponse.statusCode(),groupResponse.body());
        var groups=json.readTree(groupResponse.body());
        assertEquals(clique,groups.get("content").get(0).get("id").asString());
        var photo=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"png",photo);
        var saved=request("POST","/v2/people/"+human+"/photo","{\"photoData\":\""+java.util.Base64.getEncoder().encodeToString(photo.toByteArray())+"\"}");
        assertEquals(200,saved.statusCode(),saved.body());
        String photoUrl=json.readTree(saved.body()).get("profilePhotoUrl").asString();
        assertTrue(photoUrl.startsWith("/api/v2/people/"+human+"/photo?"));
        assertEquals(200,request("GET",photoUrl.substring(4),null).statusCode());
        assertEquals(404,request("GET","/v2/people/browse/"+owner.getHuman().getId(),null).statusCode());
        assertEquals(400,request("POST","/v2/quotes","{\"quote\":\"Bad numeric reference\",\"authorId\":"+owner.getHuman().getId()+"}").statusCode());
        var foreign=places.save(new Place("Foreign UUID",1,2,null));
        assertEquals(404,request("POST","/v2/meetings","{"+common+",\"date\":\"2026-10-01\",\"placeId\":\""+foreign.getUuid()+"\"}").statusCode());
    }

    @Test void uuidParticipantsSuggestionsAndAdminDiagnosticsUseOnlyPublicReferences() throws Exception {
        var person=people.save(new Human("Picker", "UUID",null,null,null,null,null,null));
        var other=people.save(new Human("Suggestion", "UUID",null,null,null,null,null,null));
        String body="{\"details\":{\"name\":\"UUID participants\",\"participantIds\":[]},\"date\":\"2026-10-01\"}";
        var created=request("POST","/v2/meetings",body);
        assertEquals(201,created.statusCode(),created.body());
        String interaction=json.readTree(created.body()).get("id").asString();
        var additions=request("POST","/v2/interactions/"+interaction+"/participants","{\"participantIds\":[\""+person.getUuid()+"\"]}");
        assertEquals(200,additions.statusCode(),additions.body());
        var value=json.readTree(additions.body());
        assertEquals(2,value.get("participants").size());
        for(var id:value.get("removableParticipantIds")) UUID.fromString(id.asString());
        assertEquals(204,request("DELETE","/v2/interactions/"+interaction+"/participants/"+person.getUuid(),"{}").statusCode());
        when(participantModels.models(owner.getId(),owner.getHuman().getId())).thenReturn(java.util.List.of(
            new org.example.meeter.recommendations.ParticipantModelClient.Model("guests_logistic","guests")));
        when(participantModels.suggest(anyMap())).thenReturn(java.util.List.of(other.getId()));
        var suggestions=request("POST","/v2/participant-models/suggest","{\"model\":\"guests_logistic\",\"name\":\"UUID participants\",\"placeIds\":[],\"guestIds\":[\""+person.getUuid()+"\"],\"interactionId\":\""+interaction+"\"}");
        assertEquals(200,suggestions.statusCode(),suggestions.body());
        assertEquals(other.getUuid().toString(),json.readTree(suggestions.body()).get(0).get("id").asString());
        verify(participantModels).suggest(argThat(query->((java.util.Collection<?>)query.get("guest_ids")).contains(person.getId())));
        assertEquals(403,request("GET","/v2/admin/users/people",null).statusCode());
        owner.grantRole(Role.ROLE_ADMIN); users.saveAndFlush(owner);
        var candidates=request("GET","/v2/admin/users/people",null);
        assertEquals(200,candidates.statusCode(),candidates.body());
        for(var item:json.readTree(candidates.body())) UUID.fromString(item.get("id").asString());
        var folder=Files.createTempDirectory("uuid-clique-diagnostics-");
        Files.write(folder.resolve("999999.png"),new byte[]{1,2,3});
        var report=request("POST","/v2/admin/cliques/photos/import",json.writeValueAsString(java.util.Map.of("folder",folder.toString())));
        assertEquals(200,report.statusCode(),report.body());
        assertNull(json.readTree(report.body()).get("items").get(0).get("cliqueId"));
        news.save(new org.example.meeter.news.NewsEntry("uuid-fixture","UUID news","Synthetic news",java.time.LocalDateTime.now().minusDays(1)));
        var updates=request("GET","/v2/news",null); assertEquals(200,updates.statusCode(),updates.body());
        UUID.fromString(json.readTree(updates.body()).get("content").get(0).get("id").asString());
    }

    @Test void uuidQuoteGamesRoundTripWithoutRevealingAnswersOrBroadeningOwnership() throws Exception {
        var author=people.save(new Human("UUID", "Author",null,null,null,null,null,null));
        var quote=request("POST","/v2/quotes","{\"quote\":\"UUID public quote\",\"authorId\":\""+author.getUuid()+"\",\"isPublic\":true}");
        assertEquals(201,quote.statusCode(),quote.body());
        String quoteUuid=json.readTree(quote.body()).get("id").asString(); UUID.fromString(quoteUuid);
        assertEquals(204,request("PUT","/v2/quotes/"+quoteUuid+"/vote","{\"vote\":\"PLUS\"}").statusCode());
        var gameResponse=request("POST","/v2/quote-games/single","{\"secondsPerQuestion\":30}");
        assertEquals(201,gameResponse.statusCode(),gameResponse.body());
        var game=json.readTree(gameResponse.body());
        String gameUuid=game.get("summary").get("id").asString(); UUID.fromString(gameUuid);
        String round=game.get("question").get("roundId").asString(); UUID.fromString(round);
        assertNull(game.get("question").get("quoteId")); assertNull(game.get("question").get("correctAuthor"));
        for(var candidate:game.get("candidates")) UUID.fromString(candidate.get("id").asString());
        assertEquals(round,json.readTree(request("GET","/v2/quote-games/"+gameUuid,null).body()).get("question").get("roundId").asString());
        var answer=request("POST","/v2/quote-games/"+gameUuid+"/guesses","{\"roundId\":\""+round+"\",\"authorId\":\""+author.getUuid()+"\"}");
        assertEquals(200,answer.statusCode(),answer.body());
        assertTrue(json.readTree(answer.body()).get("answers").get(0).get("correct").asBoolean());
        var scoreResponse=request("GET","/v2/quote-games/single/leaderboard",null);
        assertEquals(200,scoreResponse.statusCode(),scoreResponse.body());
        var scores=json.readTree(scoreResponse.body());
        assertEquals(1,scores.size(),scoreResponse.body());
        assertEquals(gameUuid,scores.get(0).get("gameId").asString());
        assertEquals(owner.getUuid().toString(),scores.get(0).get("playerId").asString());
        var foreign=users.save(new User("uuid-other",encoder.encode("other-password-1234"),author));
        assertEquals(200,request("POST","/v2/auth/login","{\"username\":\"uuid-other\",\"password\":\"other-password-1234\"}").statusCode());
        assertEquals(404,request("GET","/v2/quote-games/"+gameUuid,null).statusCode());
    }

    @Test void selfProfileEditsLinkedHumanWithoutTakingOwnershipAndArchivesPhotosAtomically() throws Exception {
        Human foreign = people.save(new Human("Foreign", "Creator", null, null, null, null, null, null));
        User creator = users.save(new User("profile-creator",encoder.encode("other-password-1234"),foreign));
        Long self=owner.getHuman().getId();
        jdbc.update("update people set created_by_id=? where id=?",creator.getId(),self);
        var initial=json.readTree(request("GET","/people/self",null).body());
        assertEquals(self.longValue(),initial.get("id").asLong());
        String body="{\"name\":\"  Nowe imię  \",\"surname\":\"Nazwisko\",\"birthdayDate\":\"1990-01-02\",\"fbLink\":\"https://facebook.com/example\",\"gender\":\"M\",\"livesIn\":\"Warszawa\"}";
        assertEquals(200,request("PUT","/people/self",body).statusCode());
        assertEquals("Nowe imię",people.findById(self).orElseThrow().getName());
        assertEquals(creator.getId(),jdbc.queryForObject("select created_by_id from people where id=?",Long.class,self));
        assertEquals(400,request("PUT","/people/self",body.replace("1990-01-02","2999-01-02")).statusCode());
        assertEquals(400,request("PUT","/people/self",body.replace("  Nowe imię  ","  ")).statusCode());
        var noCsrf=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/people/self"))
            .header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.discarding());
        assertEquals(403,noCsrf.statusCode());
        assertEquals(401,HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/people/self")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        var output=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20,30,BufferedImage.TYPE_INT_RGB),"png",output);
        String fileBody="{\"photoData\":\""+java.util.Base64.getEncoder().encodeToString(output.toByteArray())+"\"}";
        assertEquals(200,request("POST","/people/self/photo",fileBody).statusCode());
        String previous=people.findById(self).orElseThrow().getProfilePhotoFile();
        byte[] previousBytes=Files.readAllBytes(PHOTO_DIRECTORY.resolve(previous));
        Long previousId=jdbc.queryForObject("select profile_photo_id from people where id=?",Long.class,self);
        var previousMetadata=photoRecords.findById(previousId).orElseThrow();
        assertTrue(json.readTree(request("GET","/people/self",null).body()).get("profilePhotoUrl").asString().startsWith("/api/people/self/photo?"));
        assertEquals(200,request("GET","/people/self/photo",null).statusCode());
        jdbc.execute("alter table photos add constraint test_reject_archive check (photo_type <> 'FORMER_HUMAN_PHOTO')");
        try {
            assertEquals(409,request("POST","/people/self/photo",fileBody).statusCode());
            assertTrue(Files.exists(PHOTO_DIRECTORY.resolve(previous)));
            assertEquals(previousId,jdbc.queryForObject("select profile_photo_id from people where id=?",Long.class,self));
            try(var files=Files.list(PHOTO_DIRECTORY.resolve("former"))) { assertEquals(0,files.count()); }
        } finally { jdbc.execute("alter table photos drop constraint test_reject_archive"); }
        when(remotePhotos.download(any())).thenReturn(output.toByteArray());
        assertEquals(200,request("POST","/people/self/photo","{\"url\":\"https://example.org/new.png\"}").statusCode());
        var archived=photoRecords.findById(previousId).orElseThrow();
        assertEquals(previousMetadata.getUuid(),archived.getUuid());
        assertEquals(previousMetadata.getGenerationTime(),archived.getGenerationTime());
        assertEquals(org.example.meeter.multimedia.photo.PhotoType.FORMER_HUMAN_PHOTO,archived.getPhotoType());
        assertArrayEquals(previousBytes,Files.readAllBytes(PHOTO_DIRECTORY.resolve("former").resolve(archived.getPhotoName())));
        assertFalse(Files.exists(PHOTO_DIRECTORY.resolve(previous)));
        assertEquals(200,request("GET","/people/self/photo",null).statusCode());
        assertEquals(404,request("GET","/people/"+foreign.getId()+"/photo",null).statusCode());
        // Older portraits may have only a filename, without Photo metadata.
        String legacyFile=people.findById(self).orElseThrow().getProfilePhotoFile();
        jdbc.update("update people set profile_photo_id=null where id=?",self);
        assertEquals(200,request("POST","/people/self/photo",fileBody).statusCode());
        assertFalse(Files.exists(PHOTO_DIRECTORY.resolve(legacyFile)));
        assertEquals(2,jdbc.queryForObject("select count(*) from photos where photo_type='FORMER_HUMAN_PHOTO'",Integer.class));
    }

    @Test void participantModelSuggestionsAreAuthenticatedScopedAndCsrfProtected() throws Exception {
        var model = new org.example.meeter.recommendations.ParticipantModelClient.Model("guests_logistic", "guests");
        when(participantModels.models(owner.getId(), owner.getHuman().getId())).thenReturn(java.util.List.of(model));
        Human guest = people.save(new Human("Suggested", "Guest", null, null, null, null, null, null));
        when(participantModels.suggest(anyMap())).thenReturn(java.util.List.of(guest.getId(), owner.getHuman().getId(), guest.getId()));
        var available = request("GET", "/participant-models", null);
        assertEquals(200, available.statusCode());
        assertTrue(available.body().contains("guests_logistic"));
        var payload = "{\"model\":\"guests_logistic\",\"name\":\"Planszówki\",\"placeText\":\"Dom\",\"placeIds\":[],\"guestIds\":[],\"account_id\":999999}";
        var response = request("POST", "/participant-models/suggest", payload);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(1, json.readTree(response.body()).size());
        assertTrue(response.body().contains("Suggested Guest"));
        verify(participantModels).suggest(argThat(body -> owner.getId().equals(body.get("account_id"))
            && owner.getHuman().getId().equals(body.get("self_id"))));
        clearInvocations(participantModels);
        assertEquals(200, request("POST", "/participant-models/suggest", payload.replace("\"placeIds\":[]", "\"startDate\":\"2026-01-19\",\"placeIds\":[]")).statusCode());
        verify(participantModels).suggest(argThat(body -> "2026-01-19".equals(body.get("start_date"))));

        var anonymous = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/participant-models")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonymous.statusCode());
        var noCsrf = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/participant-models/suggest"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, noCsrf.statusCode());
    }

    @Test void participantModelsRejectForeignPlacesUnavailableModelsAndInvalidInput() throws Exception {
        when(participantModels.models(anyLong(), anyLong())).thenReturn(java.util.List.of(
            new org.example.meeter.recommendations.ParticipantModelClient.Model("context_logistic", "context")));
        var foreign = places.save(new Place("Foreign", 0, 0, null));
        String template = "{\"model\":\"%s\",\"name\":\"Planszówki\",\"placeIds\":[%s],\"guestIds\":[]}";
        assertEquals(404, request("POST", "/participant-models/suggest", template.formatted("context_logistic", foreign.getId())).statusCode());
        assertEquals(404, request("POST", "/participant-models/suggest", template.formatted("guests_logistic", "")).statusCode());
        assertEquals(400, request("POST", "/participant-models/suggest", template.formatted("../../model", "")).statusCode());
        verify(participantModels, never()).suggest(anyMap());
    }

    @Test void existingInteractionRecommendationsRetainImportedPlacesAndExcludeSavedGuests() throws Exception {
        when(participantModels.models(owner.getId(), owner.getHuman().getId())).thenReturn(java.util.List.of(
            new org.example.meeter.recommendations.ParticipantModelClient.Model("guests_logistic", "guests", true)));
        var importedPlace = places.save(new Place("Imported", 0, 0, null));
        var guest = people.save(new Human("Existing", "Guest", null, null, null, null, null, null));
        var extraGuest = people.save(new Human("New", "Guest", null, null, null, null, null, null));
        var meeting = new org.example.meeter.interactions.Meeting("Planszówki", null, java.time.LocalDate.of(2026,10,1),
            java.util.List.of(owner.getHuman(), guest), importedPlace, null);
        meeting.assignOwner(owner); interactions.saveAndFlush(meeting);
        when(participantModels.suggest(anyMap())).thenReturn(java.util.List.of(guest.getId(), extraGuest.getId()));
        var payload = "{\"model\":\"guests_logistic\",\"name\":\"Planszówki\",\"placeIds\":["+importedPlace.getId()+"],\"guestIds\":[],\"interactionId\":"+meeting.getId()+"}";
        var result = request("POST", "/participant-models/suggest", payload);
        assertEquals(200, result.statusCode(), result.body());
        assertEquals(1, json.readTree(result.body()).size());
        assertTrue(result.body().contains("New Guest"));
        verify(participantModels).suggest(argThat(body -> ((java.util.Set<?>)body.get("guest_ids")).contains(guest.getId())));
        verify(participantModels).suggest(argThat(body -> "2026-10-01".equals(body.get("start_date"))
            && ((java.util.List<?>)body.get("history")).stream().anyMatch(item ->
                "2026-10-01".equals(((java.util.Map<?,?>)item).get("start_date")))));
        assertTrue(request("GET", "/participant-models", null).body().contains("\"recommended\":true"));

        var unlinkedPlace = places.save(new Place("Other imported", 0, 0, null));
        assertEquals(404, request("POST", "/participant-models/suggest", payload.replace("["+importedPlace.getId()+"]", "["+unlinkedPlace.getId()+"]")).statusCode());
        var foreignHuman = people.save(new Human("Foreign", "Owner", null, null, null, null, null, null));
        var foreignUser = users.save(new User("foreign-"+UUID.randomUUID(), encoder.encode("test-password-123456"), foreignHuman));
        var foreign = new org.example.meeter.interactions.Meeting("Foreign", null, java.time.LocalDate.of(2026,10,1), java.util.List.of(), importedPlace, null);
        foreign.assignOwner(foreignUser); interactions.saveAndFlush(foreign);
        assertEquals(404, request("POST", "/participant-models/suggest", payload.replace("\"interactionId\":"+meeting.getId(), "\"interactionId\":"+foreign.getId())).statusCode());
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path));
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrf).method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private long id(HttpResponse<String> response) {
        assertEquals(201, response.statusCode(), response.body());
        var match = Pattern.compile("\"id\":(\\d+)").matcher(response.body());
        assertTrue(match.find(), response.body());
        return Long.parseLong(match.group(1));
    }

    @Test void adminCreatesOrdinaryAccountForExistingForeignPersonAndNewAccountCanLogIn() throws Exception {
        String endpoint="/admin/users";
        assertEquals(403,request("GET",endpoint+"/people",null).statusCode());
        assertEquals(403,request("POST",endpoint,json.writeValueAsString(java.util.Map.of("humanId",owner.getHuman().getId(),"username","blocked","password","initial-password-12345"))).statusCode());
        var linked=people.save(new Human("Already","Provision",null,null,null,null,null,null));
        var existing=users.save(new User("existing-provision",encoder.encode("existing-password-12345"),linked));
        var foreign=new Human("Free","Provision",null,null,null,null,null,null); foreign.assignOwner(existing); foreign=people.save(foreign);
        var imported=people.save(new Human("Imported","Provision",null,null,null,null,null,null));
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        var candidates=request("GET",endpoint+"/people?q=Provision&limit=1",null);
        assertEquals(200,candidates.statusCode(),candidates.body());
        assertEquals(foreign.getId().longValue(),json.readTree(candidates.body()).get(0).get("id").asLong());
        long count=users.count();
        String suppliedPassword="initial-password-12345";
        String body=json.writeValueAsString(java.util.Map.of("humanId",foreign.getId(),"username","  NEW.PROVISION  ","password",suppliedPassword,"roles",java.util.List.of("ROLE_ADMIN")));
        var noCsrf=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+endpoint)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,noCsrf.statusCode());
        var response=request("POST",endpoint,body);
        assertEquals(201,response.statusCode(),response.body());
        var credentialLines=Files.readAllLines(CREDENTIAL_FILE);
        assertEquals(2,credentialLines.size());
        assertTrue(credentialLines.get(0).equals("username,password,human_id,created_at"));
        String password=credentialLines.get(1).split(",")[1].replace("\"","");
        assertTrue(password.matches("M![A-Za-z0-9_-]{32}"));
        assertFalse(password.equals(suppliedPassword));
        assertFalse(response.body().contains(password)); assertFalse(response.body().contains(suppliedPassword)); assertFalse(response.body().contains("passwordHash"));
        var created=users.findByUsername("new.provision").orElseThrow();
        assertEquals(count+1,users.count());
        assertEquals(foreign.getId(),created.getHuman().getId());
        assertTrue(encoder.matches(password,created.getPasswordHash()));
        assertEquals(java.util.List.of("ROLE_USER"),jdbc.queryForList("select role from user_roles where user_id=?",String.class,created.getId()));
        assertEquals(existing.getId(),jdbc.queryForObject("select created_by_id from people where id=?",Long.class,foreign.getId()));
        assertEquals(owner.getId().longValue(),json.readTree(request("GET","/auth/me",null).body()).get("id").asLong());
        assertEquals(imported.getId().longValue(),json.readTree(request("GET",endpoint+"/people?q=Provision&limit=1",null).body()).get(0).get("id").asLong());
        assertEquals(409,request("POST",endpoint,json.writeValueAsString(java.util.Map.of("humanId",foreign.getId(),"username","another-login","password",password))).statusCode());
        assertEquals(409,request("POST",endpoint,json.writeValueAsString(java.util.Map.of("humanId",imported.getId(),"username","NEW.PROVISION","password",password))).statusCode());
        assertEquals(404,request("POST",endpoint,json.writeValueAsString(java.util.Map.of("humanId",999999L,"username","missing-login","password",password))).statusCode());
        for(String invalid:java.util.List.of("bad login","=formula","-formula","a".repeat(65)))
            assertEquals(400,request("POST",endpoint,json.writeValueAsString(java.util.Map.of("humanId",imported.getId(),"username",invalid))).statusCode());
        assertEquals(400,request("POST",endpoint,json.writeValueAsString(java.util.Map.of("humanId",imported.getId(),"username","bad login","password",password))).statusCode());
        assertEquals(count+1,users.count());
        var newClient=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        var csrfResponse=newClient.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
        String newCsrf=json.readTree(csrfResponse.body()).get("token").asString();
        var login=newClient.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/login")).header("Content-Type","application/json").header("X-XSRF-TOKEN",newCsrf)
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(java.util.Map.of("username","NEW.PROVISION","password",password)))).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,login.statusCode(),login.body());
        assertEquals(foreign.getId().longValue(),json.readTree(login.body()).get("humanId").asLong());
        assertFalse(login.body().contains("ROLE_ADMIN"));
        var denied=newClient.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+endpoint+"/people")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,denied.statusCode());
        var accountRequests=new java.util.ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<String>>>();
        for(String loginName:java.util.List.of("import-a","import-b")) {
            var createRequest=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+endpoint))
                .header("Content-Type","application/json").header("X-XSRF-TOKEN",csrf)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(java.util.Map.of("humanId",imported.getId(),"username",loginName,"password",password)))).build();
            accountRequests.add(client.sendAsync(createRequest,HttpResponse.BodyHandlers.ofString()));
        }
        var statuses=java.util.List.of(accountRequests.get(0).get().statusCode(),accountRequests.get(1).get().statusCode()).stream().sorted().toList();
        assertEquals(java.util.List.of(201,409),statuses);
        assertEquals(count+2,users.count());
        assertNull(jdbc.queryForObject("select created_by_id from people where id=?",Long.class,imported.getId()));
        assertEquals(0,json.readTree(request("GET",endpoint+"/people?q=Provision",null).body()).size());
        var after=Files.readAllLines(CREDENTIAL_FILE);
        assertEquals(3,after.size());
        assertTrue(after.get(1).equals(credentialLines.get(1)));
        assertFalse(after.get(2).split(",")[1].replace("\"","").equals(password));
    }

    @Test void accountCreationRollsBackWhenCredentialCsvCannotBeWritten() throws Exception {
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        var candidate=people.save(new Human("Export","Failure",null,null,null,null,null,null));
        Files.createDirectories(CREDENTIAL_FILE);
        long count=users.count();
        var response=request("POST","/admin/users",json.writeValueAsString(java.util.Map.of("humanId",candidate.getId(),"username","export-failure")));
        assertEquals(503,response.statusCode());
        assertEquals(count,users.count());
        assertFalse(users.existsByHumanId(candidate.getId()));
        Files.delete(CREDENTIAL_FILE);
        var retry=request("POST","/admin/users",json.writeValueAsString(java.util.Map.of("humanId",candidate.getId(),"username","export-failure")));
        assertEquals(201,retry.statusCode(),retry.body());
        assertEquals(2,Files.readAllLines(CREDENTIAL_FILE).size());
    }

    @Test void adminBackfillAddsOnlySelfAcrossCreatorsAndPreservesAuditsOnRepeatAndRollback() throws Exception {
        String path="/admin/participation-backfill";
        assertEquals(403,request("GET",path,null).statusCode());
        assertEquals(403,request("POST",path,"{}").statusCode());
        var foreignHuman=people.save(new Human("Foreign","Backfill",null,null,null,null,null,null));
        var other=users.save(new User("backfill-other",encoder.encode("other-password-1234"),foreignHuman));
        foreignHuman.assignOwner(other); foreignHuman=people.save(foreignHuman);
        var bride=people.save(new Human("Bride","Backfill",null,null,null,null,null,null));
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Warsaw"));
        var guests=java.util.Set.of(foreignHuman,bride);
        var meeting=new org.example.meeter.interactions.Meeting("Meeting",null,today,guests,null,null);
        var visit=new org.example.meeter.interactions.Visit("Visit",null,today.atTime(12,0),1,guests,null);
        var event=new org.example.meeter.interactions.Event("Event",null,today.atTime(12,0),today.atTime(15,0),null,null,guests,null,null);
        var trip=new org.example.meeter.interactions.Citybreak("Trip",null,today.atStartOfDay(),today.atTime(16,0),guests,java.util.Set.of());
        var invited=new org.example.meeter.interactions.Wedding("Invited",null,today.atTime(12,0),guests,foreignHuman,bride,null,null,null,false);
        invited.setWasIInvited(true);
        var uninvited=new org.example.meeter.interactions.Wedding("Not invited",null,today.atTime(12,0),guests,foreignHuman,bride,null,null,null,false);
        var flatmate=new org.example.meeter.interactions.Flatmate("Flatmate",null,today,null,bride,guests,null);
        var already=new org.example.meeter.interactions.Meeting("Existing",null,today,java.util.Set.of(owner.getHuman(),foreignHuman),null,null);
        for(var interaction:java.util.List.of(meeting,visit,event,trip,invited,uninvited,flatmate,already)) { interaction.assignOwner(other); interactions.save(interaction); }
        var before=jdbc.queryForList("select id,interaction_id,human_id,created_by_id,created_at from interaction_human order by id");
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        var preview=request("GET",path,null);
        assertEquals(200,preview.statusCode(),preview.body());
        var counts=json.readTree(preview.body()).get("missing");
        for(String type:java.util.List.of("MEETING","VISIT","EVENT","TRIP","WEDDING")) assertEquals(1,counts.get(type).asInt());
        var noCsrf=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,noCsrf.statusCode());
        long self=owner.getHuman().getId();
        jdbc.execute("alter table interaction_human add constraint test_reject_backfill check (not (human_id="+self+" and interaction_id="+trip.getId()+"))");
        try { assertTrue(request("POST",path,"{}").statusCode()>=400); }
        finally { jdbc.execute("alter table interaction_human drop constraint test_reject_backfill"); }
        assertEquals(before,jdbc.queryForList("select id,interaction_id,human_id,created_by_id,created_at from interaction_human order by id"));
        var applied=request("POST",path,"{}");
        assertEquals(200,applied.statusCode(),applied.body());
        var added=json.readTree(applied.body()).get("added");
        for(String type:java.util.List.of("MEETING","VISIT","EVENT","TRIP","WEDDING")) assertEquals(1,added.get(type).asInt());
        for(var old:before) assertEquals(old,jdbc.queryForMap("select id,interaction_id,human_id,created_by_id,created_at from interaction_human where id=?",old.get("ID")));
        assertEquals(5,jdbc.queryForObject("select count(*) from interaction_human where human_id=? and created_by_id=?",Long.class,self,owner.getId()));
        assertEquals(0,jdbc.queryForObject("select count(*) from interaction_human where human_id=? and interaction_id in (?,?)",Long.class,self,uninvited.getId(),flatmate.getId()));
        var again=json.readTree(request("POST",path,"{}").body()).get("added");
        for(String type:java.util.List.of("MEETING","VISIT","EVENT","TRIP","WEDDING")) assertEquals(0,again.get(type).asInt());
        assertEquals(other.getId(),jdbc.queryForObject("select created_by_id from interactions where id=?",Long.class,meeting.getId()));
        assertEquals(200,request("GET","/interactions/"+meeting.getId(),null).statusCode());
    }

    @Test void rankingCountsForeignSharedInteractionsButNotOwnedRecordsWithoutSelf() throws Exception {
        var target=people.save(new Human("Shared","Participation",null,null,null,null,null,null));
        var excluded=people.save(new Human("Excluded","Participation",null,null,null,null,null,null));
        var foreignHuman=people.save(new Human("Foreign","Account",null,null,null,null,null,null));
        var other=users.save(new User("ranking-participation-other",encoder.encode("other-password-1234"),foreignHuman));
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Warsaw"));
        var shared=new org.example.meeter.interactions.Visit("Shared",null,today.atTime(12,0),1,java.util.Set.of(target,owner.getHuman()),null);
        shared.assignOwner(other); interactions.save(shared);
        var event=new org.example.meeter.interactions.Event("Foreign event",null,today.minusDays(180).atStartOfDay(),today.atTime(15,0),null,today.minusDays(180).atStartOfDay(),java.util.Set.of(target,owner.getHuman()),null,null);
        event.assignOwner(other); interactions.save(event);
        var notShared=new org.example.meeter.interactions.Visit("Owned but not shared",null,today.atTime(12,0),1,java.util.Set.of(excluded),null);
        notShared.assignOwner(owner); interactions.save(notShared);
        var response=request("GET","/people/browse?q=Participation",null);
        assertEquals(200,response.statusCode(),response.body());
        var content=json.readTree(response.body()).get("content");
        assertEquals(target.getId().longValue(),content.get(0).get("person").get("id").asLong());
        assertEquals(1,content.get(0).get("visitCount").asLong());
        assertEquals(0,content.get(1).get("visitCount").asLong());
        var scores=new java.util.HashMap<Long,Double>();
        new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query(PeopleRanking.SCORES+"select human_id,points from scores",
            java.util.Map.of("ownerId",owner.getId(),"today",today,"currentHumanId",owner.getHuman().getId()),rs->{scores.put(rs.getLong(1),rs.getDouble(2));});
        assertEquals(7.0,scores.get(target.getId()),0.00001);
        assertFalse(scores.containsKey(excluded.getId()));
        jdbc.update("delete from interaction_human where interaction_id=? and human_id=?",shared.getId(),owner.getHuman().getId());
        var after=json.readTree(request("GET","/people/browse?q=Participation",null).body()).get("content");
        assertEquals(0,after.get(0).get("visitCount").asLong());
    }

    @Test void cliqueRankingUsesMeanSquaresIncludingZeroMembersAndExcludesSelfBeforePagination() throws Exception {
        long selfOnly=id(request("POST","/cliques","{\"cliqueName\":\"Ja\"}"));
        long strong=id(request("POST","/cliques","{\"cliqueName\":\"Bliska\"}"));
        long balanced=id(request("POST","/cliques","{\"cliqueName\":\"Regularna\"}"));
        long empty=id(request("POST","/cliques","{\"cliqueName\":\"Pusta\"}"));
        jdbc.update("update people set clique_id=? where id=?",selfOnly,owner.getHuman().getId());
        long visitor=id(request("POST","/people","{\"name\":\"Gość\",\"cliqueId\":"+strong+"}"));
        id(request("POST","/people","{\"name\":\"Bez historii\",\"cliqueId\":"+strong+"}"));
        long first=id(request("POST","/people","{\"name\":\"Anna\",\"cliqueId\":"+balanced+"}"));
        long second=id(request("POST","/people","{\"name\":\"Zofia\",\"cliqueId\":"+balanced+"}"));
        String today=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Warsaw")).toString();
        id(request("POST","/visits","{\"visitDate\":\""+today+"T12:00:00\",\"duration\":1,\"details\":{\"name\":\"Wizyta\",\"participantIds\":["+visitor+"]}}"));
        id(request("POST","/meetings","{\"date\":\""+today+"\",\"details\":{\"name\":\"Spotkanie\",\"participantIds\":["+first+","+second+"]}}"));
        var response=request("GET","/cliques/browse?size=1",null);
        assertEquals(200,response.statusCode(),response.body());
        assertEquals(strong,json.readTree(response.body()).get("content").get(0).get("id").asLong());
        var all=json.readTree(request("GET","/cliques/browse",null).body()).get("content");
        assertEquals(balanced,all.get(1).get("id").asLong());
        assertEquals(selfOnly,all.get(2).get("id").asLong());
        assertEquals(0,all.get(2).get("members").size());
        assertEquals(empty,all.get(3).get("id").asLong());
        for(int n=0;n<3;n++) id(request("POST","/people","{\"name\":\"Bez kontaktu\",\"cliqueId\":"+strong+"}"));
        var reordered=request("GET","/cliques/browse?size=1",null);
        assertEquals(balanced,json.readTree(reordered.body()).get("content").get(0).get("id").asLong());
        assertFalse(reordered.body().contains("points"));
        assertFalse(reordered.body().contains("score"));
    }

    @Test void cliqueDirectoryOrdersGroupsAndMembersAndProtectsPhotos() throws Exception {
        long first=id(request("POST","/cliques","{\"cliqueName\":\"Zulu\"}"));
        long second=id(request("POST","/cliques","{\"cliqueName\":\"Alfa\"}"));
        long anna=id(request("POST","/people","{\"name\":\"Anna\",\"cliqueId\":"+first+"}"));
        long zofia=id(request("POST","/people","{\"name\":\"Zofia\",\"cliqueId\":"+first+"}"));
        id(request("POST","/meetings","{\"date\":\"2020-01-01\",\"details\":{\"name\":\"Spotkanie\",\"participantIds\":["+zofia+"]}}"));
        var foreign=cliques.save(new Clique("Obca",null));
        // An unowned/imported clique is not automatically made public by this new view.
        var response=request("GET","/cliques/browse?size=1",null);
        assertEquals(200,response.statusCode(),response.body());
        var body=json.readTree(response.body());
        assertEquals(2,body.get("totalElements").asLong());
        assertTrue(body.get("hasNext").asBoolean());
        var card=body.get("content").get(0);
        assertEquals(first,card.get("id").asLong());
        assertEquals(zofia,card.get("members").get(0).get("id").asLong());
        assertEquals(anna,card.get("members").get(1).get("id").asLong());
        assertFalse(response.body().contains("points"));
        assertEquals(second,json.readTree(request("GET","/cliques/browse?page=1&size=1",null).body()).get("content").get(0).get("id").asLong());
        assertEquals(404,request("GET","/cliques/"+foreign.getId()+"/photo",null).statusCode());
        Path folder=Files.createTempDirectory("meeter-clique-import-");
        ImageIO.write(new BufferedImage(20,10,BufferedImage.TYPE_INT_RGB),"png",folder.resolve(first+".png").toFile());
        Files.writeString(folder.resolve(second+".jpg"),"invalid image");
        Files.writeString(folder.resolve("notes.txt"),"not a photo");
        String input=json.writeValueAsString(java.util.Map.of("folder",folder.toString()));
        assertEquals(403,request("POST","/admin/cliques/photos/import",input).statusCode());
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        var imported=request("POST","/admin/cliques/photos/import",input);
        assertEquals(200,imported.statusCode(),imported.body());
        var report=json.readTree(imported.body());
        assertEquals(1,report.get("imported").asLong());
        assertEquals(1,report.get("failed").asLong());
        assertEquals(1,report.get("skipped").asLong());
        var metadata=photoRecords.findAll();
        assertEquals(1,metadata.size());
        assertEquals(org.example.meeter.multimedia.photo.PhotoType.CLIQUE,metadata.getFirst().getPhotoType());
        assertTrue(metadata.getFirst().getPhotoName().matches("[a-f0-9-]{36}\\.jpg"));
        assertTrue(Files.exists(folder.resolve(first+".png")));
        assertEquals(0,json.readTree(request("POST","/admin/cliques/photos/import",input).body()).get("imported").asLong());
        assertEquals(1,photoRecords.count());
        assertEquals(200,request("GET","/cliques/"+first+"/photo",null).statusCode());
        assertEquals(404,request("GET","/cliques/"+second+"/photo",null).statusCode());
        assertEquals(400,request("POST","/admin/cliques/photos/import",json.writeValueAsString(java.util.Map.of("folder",folder.resolve("missing").toString()))).statusCode());
    }

    @Test void editsAllInteractionTypesWithoutReplacingIdentityOrRelationshipMetadata() throws Exception {
        long person = id(request("POST", "/people", "{\"name\":\"Uczestnik\"}"));
        long bride = id(request("POST", "/people", "{\"name\":\"Panna młoda\"}"));
        long place = id(request("POST", "/places", "{\"name\":\"Miejsce\",\"latitude\":52,\"longitude\":21}"));
        String details = "\"details\":{\"name\":\"Przed edycją\",\"longDesc\":\"Opis\",\"participantIds\":[]}";
        String[] endpoints = {"meetings", "visits", "events", "trips", "flatmates", "weddings"};
        String[] fields = {
            "\"date\":\"2026-10-10\",\"placeId\":" + place + ",\"placeString\":\"Opis miejsca\"",
            "\"visitDate\":\"2026-10-10T18:00:17\",\"duration\":3,\"placeId\":" + place,
            "\"startDate\":\"2026-10-10T18:00:17\",\"stopDate\":\"2026-10-11T20:00:00\",\"meComingDate\":\"2026-10-10T19:00:00\",\"placeId\":" + place,
            "\"startDate\":\"2026-10-10T18:00:17\",\"endDate\":\"2026-10-11T20:00:00\",\"placeIds\":[" + place + "]",
            "\"startDate\":\"2026-10-10\",\"humanId\":" + person + ",\"placeId\":" + place,
            "\"dateTime\":\"2026-10-10T18:00:17\",\"groomId\":" + person + ",\"brideId\":" + bride + ",\"ceremonyPlaceId\":" + place + ",\"hadAfter\":true,\"wasIInvited\":true"
        };
        for (int index = 0; index < endpoints.length; index++) {
            String path = "/" + endpoints[index];
            String body = "{" + details + "," + fields[index] + "}";
            var created = request("POST", path, body);
            long interactionId = id(created);
            // Read the persisted precision rather than comparing the constructor's nanoseconds with database microseconds.
            var before = json.readTree(request("GET", "/interactions/" + interactionId, null).body());
            var links = jdbc.queryForList("select id, human_id, created_at, created_by_id from interaction_human where interaction_id=? order by id", interactionId);
            var route = jdbc.queryForList("select id, place_id, created_at, created_by_id from trip_place where trip_id=? order by id", interactionId);
            var updated = request("PUT", path + "/" + interactionId, body.replace("Przed edycją", "Po edycji").replace("Opis\"", "Nowy opis\""));
            assertEquals(200, updated.statusCode(), updated.body());
            var after = json.readTree(updated.body());
            assertEquals("Po edycji", after.get("name").asString());
            for (String field : java.util.List.of("id", "uuid", "createdAt", "startDate", "endDate", "details", "participants", "place"))
                assertEquals(before.get(field), after.get(field), path + " " + field);
            assertEquals(links, jdbc.queryForList("select id, human_id, created_at, created_by_id from interaction_human where interaction_id=? order by id", interactionId));
            assertEquals(route, jdbc.queryForList("select id, place_id, created_at, created_by_id from trip_place where trip_id=? order by id", interactionId));
        }
        assertEquals(6, interactions.count());
    }

    @Test void editsValidateOwnershipDatesAndCsrfAndRetainExplicitParticipantRemoval() throws Exception {
        String body = "{\"details\":{\"name\":\"Pierwotna nazwa\",\"participantIds\":[]},\"date\":\"2026-10-10\"}";
        long meeting = id(request("POST", "/meetings", body));
        var deletion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/interactions/" + meeting + "/participants/" + owner.getHuman().getId()))
            .header("X-XSRF-TOKEN", csrf).DELETE().build();
        assertEquals(204, client.send(deletion, HttpResponse.BodyHandlers.discarding()).statusCode());
        var update = request("PUT", "/meetings/" + meeting, body.replace("2026-10-10", "2026-10-12"));
        assertEquals(200, update.statusCode(), update.body());
        assertEquals(0, json.readTree(update.body()).get("participants").size());
        assertEquals("2026-10-12T00:00:00", json.readTree(update.body()).get("startDate").asString());
        assertEquals(404, request("PUT", "/visits/" + meeting, "{\"details\":{\"name\":\"Inny typ\",\"participantIds\":[]},\"visitDate\":\"2026-10-10T18:00:00\",\"duration\":1}").statusCode());
        assertEquals(404, request("PUT", "/meetings/999999", body).statusCode());
        assertEquals(400, request("PUT", "/meetings/" + meeting, body.replace("[]", "[" + owner.getHuman().getId() + "]")).statusCode());
        assertEquals(404, request("PUT", "/meetings/" + meeting, body.replace("\"date\"", "\"placeId\":999999,\"date\"")).statusCode());
        var noCsrf = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/meetings/" + meeting))
            .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        assertEquals(403, client.send(noCsrf, HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(403, HttpClient.newHttpClient().send(noCsrf, HttpResponse.BodyHandlers.discarding()).statusCode());
        var anonymous = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var anonymousCsrf = anonymous.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        var anonymousPut = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/meetings/" + meeting))
            .header("Content-Type", "application/json").header("X-XSRF-TOKEN", json.readTree(anonymousCsrf.body()).get("token").asString())
            .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        assertEquals(401, anonymous.send(anonymousPut, HttpResponse.BodyHandlers.discarding()).statusCode());
        Human foreign = people.save(new Human("Obcy", "Autor", null, null, null, null, null, null));
        User other = users.save(new User("interaction-edit-other", encoder.encode("other-password-1234"), foreign));
        foreign.assignOwner(other); people.save(foreign);
        var otherMeeting = new org.example.meeter.interactions.Meeting("Obca interakcja", null, java.time.LocalDate.of(2026,10,10), java.util.List.of(foreign), null, null);
        otherMeeting.assignOwner(other); interactions.saveAndFlush(otherMeeting);
        assertEquals(404, request("PUT", "/meetings/" + otherMeeting.getId(), body).statusCode());
        owner.grantRole(org.example.meeter.auth.Role.ROLE_ADMIN); users.saveAndFlush(owner);
        assertEquals(404, request("PUT", "/meetings/" + otherMeeting.getId(), body).statusCode());
    }

    @Test void tripEditsPreserveRetainedLinksAndEventEditsAreAtomicWithSharedPhotos() throws Exception {
        long firstPlace = id(request("POST", "/places", "{\"name\":\"Pierwsze miejsce\",\"latitude\":52,\"longitude\":21}"));
        long secondPlace = id(request("POST", "/places", "{\"name\":\"Drugie miejsce\",\"latitude\":51,\"longitude\":20}"));
        String tripBody = "{\"details\":{\"name\":\"Podróż\",\"participantIds\":[]},\"startDate\":\"2026-10-10T18:00:00\",\"endDate\":\"2026-10-11T20:00:00\",\"placeIds\":[" + firstPlace + "]}";
        long trip = id(request("POST", "/trips", tripBody));
        var originalLink = jdbc.queryForMap("select id,created_at,created_by_id from trip_place where trip_id=? and place_id=?", trip, firstPlace);
        String expanded = tripBody.replace("[" + firstPlace + "]", "[" + firstPlace + "," + secondPlace + "]");
        assertEquals(200, request("PUT", "/trips/" + trip, expanded).statusCode());
        assertEquals(originalLink, jdbc.queryForMap("select id,created_at,created_by_id from trip_place where trip_id=? and place_id=?", trip, firstPlace));
        assertEquals(owner.getId(), jdbc.queryForObject("select created_by_id from trip_place where trip_id=? and place_id=?", Long.class, trip, secondPlace));
        assertEquals(200, request("PUT", "/trips/" + trip, tripBody.replace("[" + firstPlace + "]", "[" + secondPlace + "]")).statusCode());
        assertEquals(2, places.count());
        assertEquals(1, jdbc.queryForObject("select count(*) from trip_place where trip_id=?", Integer.class, trip));
        assertEquals(400, request("PUT", "/trips/" + trip, tripBody.replace("2026-10-11T20:00:00", "2026-10-09T20:00:00")).statusCode());

        var buffer = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(40,20,BufferedImage.TYPE_INT_RGB), "png", buffer);
        String encoded = java.util.Base64.getEncoder().encodeToString(buffer.toByteArray());
        String eventBase = "{\"details\":{\"name\":\"Wydarzenie\",\"participantIds\":[]},\"startDate\":\"2026-10-10T18:00:00\",\"stopDate\":\"2026-10-10T20:00:00\"";
        long event = id(request("POST", "/events", eventBase + ",\"photoData\":\"" + encoded + "\"}"));
        String originalFile = jdbc.queryForObject("select photo_file from events where id=?", String.class, event);
        long reused = id(request("POST", "/events", eventBase + ",\"photoEventId\":" + event + "}"));
        assertEquals(200, request("PUT", "/events/" + event, eventBase + ",\"photoEventId\":" + event + "}").statusCode());
        assertEquals(originalFile, jdbc.queryForObject("select photo_file from events where id=?", String.class, event));
        assertEquals(400, request("PUT", "/events/" + event, eventBase.replace("Wydarzenie", "Nie zapisuj") + ",\"photoData\":\"!invalid!\"}").statusCode());
        assertEquals("Wydarzenie", json.readTree(request("GET", "/interactions/" + event, null).body()).get("name").asString());
        assertEquals(originalFile, jdbc.queryForObject("select photo_file from events where id=?", String.class, event));
        assertEquals(400, request("PUT", "/events/" + event, eventBase + ",\"meLeavingDate\":\"2026-10-10T21:00:00\"}").statusCode());
        assertEquals(200, request("PUT", "/events/" + event, eventBase + "}").statusCode());
        assertNull(jdbc.queryForObject("select photo_file from events where id=?", String.class, event));
        assertTrue(Files.exists(PHOTO_DIRECTORY.resolve("events").resolve(originalFile)));
        assertEquals(200, request("GET", "/events/" + reused + "/photo", null).statusCode());
    }

    @Test void profileLastSeenUsesSharedPastInteractionsIncludingForeignCreators() throws Exception {
        var person=people.save(new Human("Ostatnio","Widziany",null,null,null,null,null,null));
        String path="/people/browse/"+person.getId();
        assertTrue(json.readTree(request("GET",path,null).body()).get("lastSeen").isNull());
        var today=java.time.LocalDate.now();
        var foreignHuman=people.save(new Human("Inny","Twórca",null,null,null,null,null,null));
        var foreign=users.save(new User("last-seen-other",encoder.encode("other-password-1234"),foreignHuman));
        var shared=new org.example.meeter.interactions.Meeting("Wspólna kawa",null,today.minusDays(3),java.util.List.of(person,owner.getHuman()),null,null);
        shared.assignOwner(foreign); interactions.save(shared);
        var future=new org.example.meeter.interactions.Meeting("Przyszłe spotkanie",null,today.plusDays(2),java.util.List.of(person,owner.getHuman()),null,null);
        future.assignOwner(owner); interactions.save(future);
        var unrelated=new org.example.meeter.interactions.Meeting("Bez mojego udziału",null,today.minusDays(1),java.util.List.of(person),null,null);
        unrelated.assignOwner(owner); interactions.save(unrelated);
        for(boolean admin : new boolean[]{false,true}) {
            if(admin) { owner.grantRole(Role.ROLE_ADMIN); users.save(owner); }
            var seen=json.readTree(request("GET",path,null).body()).get("lastSeen");
            assertEquals(3,seen.get("daysAgo").asInt());
            assertEquals("Wspólna kawa",seen.get("interactionName").asString());
        }
    }

    @Test void profileBuildsLazyHomeAndCliqueCapitalInsideTransaction() throws Exception {
        var home=new Place("Dom profilu",52.2,21.0,null); home.assignOwner(owner); home=places.save(home);
        var capital=new Place("Stolica kliki",50.0,19.0,null); capital.assignOwner(owner); capital=places.save(capital);
        var clique=new Clique("Klika profilu",capital); clique.assignOwner(owner); clique=cliques.save(clique);
        var human=new Human("Osoba","Z relacjami",null,null,null,null,home,clique); human.assignOwner(owner); human=people.save(human);
        for(int repeat=0;repeat<3;repeat++) {
            var response=request("GET","/people/browse/"+human.getId(),null);
            assertEquals(200,response.statusCode(),response.body());
            var profile=json.readTree(response.body());
            assertEquals("Dom profilu",profile.get("person").get("home").get("name").asString());
            assertEquals("Stolica kliki",profile.get("person").get("clique").get("cliqueCapital").get("name").asString());
            assertTrue(profile.get("canEditPhoto").asBoolean());
            assertTrue(profile.get("canEditSuperpowers").asBoolean());
        }
        assertEquals(200,request("GET","/auth/me",null).statusCode());
    }

    @Test void serverFailureKeepsOriginalStatusAndDoesNotInvalidateSession() throws Exception {
        var failure=request("GET","/test/server-error",null);
        assertEquals(500,failure.statusCode(),failure.body());
        assertFalse(failure.body().contains("Synthetic server failure"));
        assertEquals(200,request("GET","/auth/me",null).statusCode());
        // Cookie path is /api, so explicitly attach it to test authenticated direct access.
        var direct=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/error")).header("Cookie",loginCookie).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,direct.statusCode());
        var anonymous=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/test/server-error")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(401,anonymous.statusCode());
    }

    @Test void categoryIconsRequireAuthenticationAndStayInsideTheirDirectory() throws Exception {
        var directory=PHOTO_DIRECTORY.resolve("categories"); Files.createDirectories(directory);
        ImageIO.write(new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB),"png",directory.resolve("cafe.png").toFile());
        var icon=request("GET","/places/category-icons/cafe.png",null);
        assertEquals(200,icon.statusCode()); assertEquals("image/png",icon.headers().firstValue("content-type").orElseThrow());
        assertEquals(404,request("GET","/places/category-icons/missing.png",null).statusCode());
        assertEquals(404,request("GET","/places/category-icons/initial-login.txt",null).statusCode());
        var anonymous=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/places/category-icons/cafe.png")).GET().build(),HttpResponse.BodyHandlers.discarding());
        assertEquals(401,anonymous.statusCode());
    }

    @Test void superpowerDictionaryStoresCreatorTimeAndUuidContract() throws Exception {
        var created=request("POST","/v2/superpowers","{\"superpowerName\":\"  Latanie  \"}");
        assertEquals(201,created.statusCode(),created.body());
        var body=json.readTree(created.body());
        UUID uuid=UUID.fromString(body.get("id").asString());
        assertEquals("Latanie",body.get("superpowerName").asString());
        assertEquals(owner.getId(),jdbc.queryForObject("select added_by_id from superpowers where uuid = ?",Long.class,uuid));
        assertEquals(1,jdbc.queryForObject("select count(*) from superpowers where uuid = ? and add_time is not null and superpower_photo_id is null",Integer.class,uuid).intValue());
        assertEquals(400,request("POST","/v2/superpowers","{\"superpowerName\":\"   \"}").statusCode());
        var single=request("GET","/v2/superpowers/"+uuid,null);
        assertEquals(200,single.statusCode(),single.body());
        assertEquals(uuid.toString(),json.readTree(single.body()).get("id").asString());
        var listed=request("GET","/v2/superpowers",null);
        assertEquals(200,listed.statusCode(),listed.body());
        assertTrue(listed.body().contains("Latanie"));
        assertEquals(404,request("GET","/v2/superpowers/"+UUID.randomUUID(),null).statusCode());
    }

    @Test void humanSuperpowerLinksRequireOwnedHumansAndUseUuidContract() throws Exception {
        var human=people.save(new Human("Supermoc", "Test",null,null,null,null,null,null));
        human.assignOwner(owner); people.save(human);
        var foreign=people.save(new Human("Obcy", "Test",null,null,null,null,null,null));
        var superpower=superpowers.save(new Superpower("Latanie", owner));
        var empty=request("GET","/v2/people/"+human.getUuid()+"/superpowers",null);
        assertEquals(200,empty.statusCode(),empty.body());
        assertTrue(empty.body().contains("[]"));
        assertEquals(404,request("POST","/people/"+foreign.getId()+"/superpowers","{\"superpowerId\":"+superpower.getId()+"}").statusCode());
        assertEquals(404,request("POST","/people/"+human.getId()+"/superpowers","{\"superpowerId\":999999}").statusCode());
        var created=request("POST","/v2/people/"+human.getUuid()+"/superpowers","{\"superpowerId\":\""+superpower.getUuid()+"\"}");
        assertEquals(201,created.statusCode(),created.body());
        assertEquals(superpower.getUuid().toString(),json.readTree(created.body()).get("superpowerId").asString());
        assertEquals(owner.getId(),jdbc.queryForObject("select added_by_id from human_superpower where human_id = ?",Long.class,human.getId()));
        assertEquals(1,jdbc.queryForObject("select count(*) from human_superpower where add_time is not null",Integer.class).intValue());
        assertEquals(201,request("POST","/v2/people/"+human.getUuid()+"/superpowers","{\"superpowerId\":\""+superpower.getUuid()+"\"}").statusCode());
        var listed=request("GET","/v2/people/"+human.getUuid()+"/superpowers",null);
        assertEquals(200,listed.statusCode(),listed.body());
        assertTrue(listed.body().contains("Latanie"));
        var deleted=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v2/people/"+human.getUuid()+"/superpowers/"+superpower.getUuid())).header("X-XSRF-TOKEN",csrf).DELETE().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(204,deleted.statusCode(),deleted.body());
        var missing=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/people/"+human.getId()+"/superpowers/"+superpower.getId())).header("X-XSRF-TOKEN",csrf).DELETE().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(404,missing.statusCode(),missing.body());
    }

    @Test void superpowerLinksAllowAdminSelfCreatorAndSpouse() throws Exception {
        var superpower=superpowers.save(new Superpower("Zwinność",owner));
        var stranger=people.save(new Human("Obcy","Świadek",null,null,null,null,null,null));
        var spouse=people.save(new Human("Małżonek","Test",null,null,null,null,null,null));
        var spouseUser=users.save(new User("super-spouse",encoder.encode("spouse-password-1234"),spouse));
        spouse.assignOwner(spouseUser); people.save(spouse);
        var self=people.save(new Human("Sama","Sobie",null,null,null,null,null,null));
        var selfUser=users.save(new User("super-self",encoder.encode("self-password-1234"),self));
        self.assignOwner(selfUser); people.save(self);
        interactions.save(new org.example.meeter.interactions.Wedding("Ślub świadka",null,
            java.time.LocalDateTime.now(),java.util.Set.of(),stranger,spouse,null,null,null,false));
        String link="{\"superpowerId\":"+superpower.getId()+"}";
        String path="/people/"+stranger.getId()+"/superpowers";
        // The stranger has no creator: an unrelated account gets no access yet.
        assertEquals(404,request("POST",path,link).statusCode());
        // The spouse of the person may list, add and remove.
        assertEquals(200,request("POST","/auth/login","{\"username\":\"super-spouse\",\"password\":\"spouse-password-1234\"}").statusCode());
        assertEquals(200,request("GET",path,null).statusCode());
        assertEquals(201,request("POST",path,link).statusCode());
        assertTrue(json.readTree(request("GET","/people/browse/"+stranger.getId(),null).body()).get("canEditSuperpowers").asBoolean());
        var removed=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path+"/"+superpower.getId()))
            .header("X-XSRF-TOKEN",csrf).DELETE().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(204,removed.statusCode(),removed.body());
        // The person themselves manages their own record, but not a stranger's.
        assertEquals(200,request("POST","/auth/login","{\"username\":\"super-self\",\"password\":\"self-password-1234\"}").statusCode());
        assertEquals(201,request("POST","/people/"+self.getId()+"/superpowers",link).statusCode());
        assertTrue(json.readTree(request("GET","/people/browse/"+self.getId(),null).body()).get("canEditSuperpowers").asBoolean());
        assertEquals(404,request("POST",path,link).statusCode());
        // An administrator manages every person's superpowers.
        assertEquals(200,request("POST","/auth/login","{\"username\":\""+username+"\",\"password\":\"test-password-123456\"}").statusCode());
        assertEquals(404,request("POST",path,link).statusCode());
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        assertEquals(201,request("POST",path,link).statusCode());
        assertEquals(200,request("GET",path,null).statusCode());
        assertTrue(json.readTree(request("GET","/people/browse/"+stranger.getId(),null).body()).get("canEditSuperpowers").asBoolean());
    }

    @Test void profileAllowsCreatorToAddParticipantsToMeetingsEventsAndTrips() throws Exception {
        var subject=people.save(new Human("Profil", "Uczestnik",null,null,null,null,null,null));
        var addition=people.save(new Human("Nowy", "Uczestnik",null,null,null,null,null,null));
        String common=details("Interakcja profilu",subject.getId());
        var ids=java.util.Map.of(
            "MEETING",id(request("POST","/meetings","{"+common+",\"date\":\"2026-10-01\"}")),
            "EVENT",id(request("POST","/events","{"+common+",\"startDate\":\"2026-10-01T12:00\",\"stopDate\":\"2026-10-01T20:00\"}")),
            "TRIP",id(request("POST","/trips","{"+common+",\"startDate\":\"2026-10-01T12:00\",\"endDate\":\"2026-10-03T20:00\",\"placeIds\":[]}")));
        for(var entry:ids.entrySet()) {
            String profilePath="/people/browse/"+subject.getId()+"/interactions?type="+entry.getKey();
            var before=json.readTree(request("GET",profilePath,null).body()).get("content").get(0);
            assertTrue(before.get("canAddParticipants").asBoolean());
            assertEquals(0,before.get("removableParticipantIds").size());
            var metadata=jdbc.queryForMap("select id, created_at, created_by_id from interaction_human where interaction_id=? and human_id=?",entry.getValue(),subject.getId());
            assertEquals(200,request("POST","/interactions/"+entry.getValue()+"/participants","{\"participantIds\":["+addition.getId()+"]}").statusCode());
            var after=json.readTree(request("GET",profilePath,null).body()).get("content").get(0);
            assertEquals(3,after.get("participants").size());
            assertEquals(metadata,jdbc.queryForMap("select id, created_at, created_by_id from interaction_human where interaction_id=? and human_id=?",entry.getValue(),subject.getId()));
            assertEquals(owner.getId(),jdbc.queryForObject("select created_by_id from interaction_human where interaction_id=? and human_id=?",Long.class,entry.getValue(),addition.getId()));
        }
    }

    @Test void profileFiltersInteractionsMapAndQuotesByViewerBeforePagination() throws Exception {
        var subject=people.save(new Human("Osoba", "Profil",null,null,null,null,null,null));
        var foreignHuman=people.save(new Human("Inny", "Autor",null,null,null,null,null,null));
        var foreign=users.save(new User("profile-other",encoder.encode("other-password-1234"),foreignHuman));
        var location=new Place("Widoczne miejsce",52.2,21.0,"kawiarnia"); location.assignOwner(foreign); location=places.save(location);
        var secretLocation=new Place("Ukryte miejsce",50.0,19.0,null); secretLocation.assignOwner(foreign); secretLocation=places.save(secretLocation);
        var hiddenOnlyLocation=new Place("Tylko ukryta interakcja",51.0,18.0,null); hiddenOnlyLocation.assignOwner(foreign); hiddenOnlyLocation=places.save(hiddenOnlyLocation);
        var when=java.time.LocalDateTime.of(2026,10,1,12,0);
        var visible=new org.example.meeter.interactions.Visit("Widoczna wizyta",null,when,1,java.util.Set.of(subject,owner.getHuman()),location);
        visible.assignOwner(foreign); interactions.save(visible);
        var hidden=new org.example.meeter.interactions.Visit("Ukryta wizyta",null,when.plusDays(1),1,java.util.Set.of(subject,foreignHuman),hiddenOnlyLocation);
        hidden.assignOwner(foreign); interactions.save(hidden);
        var trip=new org.example.meeter.interactions.Citybreak("Wspólna podróż",null,when,when.plusDays(2),java.util.Set.of(subject,owner.getHuman()),java.util.Set.of(location,secretLocation));
        trip.assignOwner(foreign); interactions.save(trip);
        var publicQuote=new org.example.meeter.quote.Quote("Publiczny cytat profilu",subject,true); publicQuote.assignOwner(foreign); quotes.save(publicQuote);
        var privateQuote=new org.example.meeter.quote.Quote("Prywatny cytat profilu",subject,false); privateQuote.assignOwner(owner); quotes.save(privateQuote);
        String base="/people/browse/"+subject.getId();
        assertFalse(json.readTree(request("GET",base,null).body()).get("canEditPhoto").asBoolean());
        var visits=json.readTree(request("GET",base+"/interactions?type=VISIT&size=1",null).body());
        assertEquals(1,visits.get("totalElements").asInt()); assertFalse(visits.get("hasNext").asBoolean());
        assertEquals("Widoczna wizyta",visits.get("content").get(0).get("name").asString());
        assertFalse(visits.get("content").get(0).get("canAddParticipants").asBoolean());
        assertEquals(200,request("GET","/interactions/"+visible.getId(),null).statusCode());
        var map=json.readTree(request("GET",base+"/map",null).body()); assertEquals(2,map.size());
        assertEquals("kawiarnia",map.get(0).get("category").asString());
        assertEquals(location.getId().longValue(),map.get(0).get("id").asLong());
        assertEquals("Widoczne miejsce",map.get(0).get("name").asString());
        assertFalse(map.toString().contains("Widoczna wizyta"));
        assertFalse(map.toString().contains("Wspólna podróż"));
        assertFalse(map.toString().contains("Tylko ukryta interakcja"));
        assertFalse(map.toString().contains("Ukryta wizyta"));
        var trips=json.readTree(request("GET",base+"/interactions?type=TRIP",null).body());
        assertEquals(2,trips.get("content").get(0).get("details").get("places").size());
        assertEquals(1,json.readTree(request("GET",base+"/quotes",null).body()).get("totalElements").asInt());
        // A quote's creator alone does not gain access to another author's private quotes.
        assertEquals(404,request("GET","/quotes/"+privateQuote.getId(),null).statusCode());
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        assertEquals(2,json.readTree(request("GET",base+"/interactions?type=VISIT&size=1",null).body()).get("totalElements").asInt());
        assertFalse(json.readTree(request("GET",base+"/interactions?type=TRIP",null).body()).get("content").get(0).get("canAddParticipants").asBoolean());
        assertEquals(404,request("POST","/interactions/"+trip.getId()+"/participants","{\"participantIds\":["+foreignHuman.getId()+"]}").statusCode());
        assertEquals(3,json.readTree(request("GET",base+"/map",null).body()).size());
        assertEquals(2,json.readTree(request("GET",base+"/quotes",null).body()).get("totalElements").asInt());
        assertEquals(400,request("GET",base+"/quotes?page=-1",null).statusCode());
        assertEquals(400,request("GET",base+"/interactions?size=101",null).statusCode());
        assertEquals(404,request("GET","/people/browse/999999999/map",null).statusCode());
        // The author sees their own private quotes by linked Human identity.
        var ownQuote=new org.example.meeter.quote.Quote("Mój prywatny cytat",owner.getHuman(),false); ownQuote.assignOwner(foreign); quotes.save(ownQuote);
        // Remove admin to ensure this exercises the author rule.
        jdbc.update("delete from user_roles where user_id=? and role='ROLE_ADMIN'",owner.getId());
        assertEquals(1,json.readTree(request("GET","/people/browse/"+owner.getHuman().getId()+"/quotes",null).body()).get("totalElements").asInt());
    }

    @Test void profilePhotoFileUpdatesKeepOwnershipAndAtomicFailure() throws Exception {
        var person=new Human("Zdjęcie","Profil",null,null,null,null,null,null); person.assignOwner(owner); person=people.save(person);
        var output=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(25,30,BufferedImage.TYPE_INT_RGB),"png",output);
        String body="{\"photoData\":\""+java.util.Base64.getEncoder().encodeToString(output.toByteArray())+"\"}";
        String path="/people/"+person.getId()+"/photo";
        var saved=request("POST",path,body); assertEquals(200,saved.statusCode(),saved.body());
        String photo=json.readTree(saved.body()).get("profilePhotoUrl").asString();
        assertEquals(200,request("GET",photo.substring(4),null).statusCode());
        assertEquals(400,request("POST",path,"{\"photoData\":\"bm90LWltYWdl\"}").statusCode());
        assertEquals(photo,people.findById(person.getId()).orElseThrow().getProfilePhotoUrl());
        assertEquals(400,request("POST",path,"{\"url\":\"https://example.org/photo.jpg\",\"photoData\":\"YQ==\"}").statusCode());
        assertEquals(400,request("POST",path,"{\"photoData\":\"!invalid!\"}").statusCode());
        var foreign=people.save(new Human("Cudza","Osoba",null,null,null,null,null,null));
        String foreignPath="/people/"+foreign.getId()+"/photo";
        assertEquals(404,request("POST",foreignPath,body).statusCode());
        assertFalse(json.readTree(request("GET","/people/browse/"+foreign.getId(),null).body()).get("canEditPhoto").asBoolean());
        assertFalse(json.readTree(request("GET","/people/browse/"+foreign.getId(),null).body()).get("canEditSuperpowers").asBoolean());
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        assertTrue(json.readTree(request("GET","/people/browse/"+foreign.getId(),null).body()).get("canEditPhoto").asBoolean());
        // Admins also manage superpowers, unlike photo ownership for unrelated records.
        assertTrue(json.readTree(request("GET","/people/browse/"+foreign.getId(),null).body()).get("canEditSuperpowers").asBoolean());
        var adminPhoto=request("POST",foreignPath,body); assertEquals(200,adminPhoto.statusCode(),adminPhoto.body());
        String browsePhoto=json.readTree(adminPhoto.body()).get("profilePhotoUrl").asString();
        assertTrue(browsePhoto.startsWith("/api/people/browse/"));
        assertEquals(200,request("GET",browsePhoto.substring(4),null).statusCode());
        assertEquals(404,request("GET",foreignPath,null).statusCode());
        assertNull(people.findById(foreign.getId()).orElseThrow().getCreatedBy());
        assertEquals(404,request("POST","/people/999999999/photo",body).statusCode());
        var noCsrf=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,noCsrf.statusCode());
    }

    @Test void browsesAllPeopleByOwnedVisitCountWithPaginationAndLiteralSearch() throws Exception {
        Human anna=new Human("Anna", "Ranking",null,null,null,null,null,null); anna.assignOwner(owner); anna=people.save(anna);
        Human zenon=new Human("Zenon", "Ranking",null,null,null,null,null,null); zenon.assignOwner(owner); zenon=people.save(zenon);
        Human zero=new Human("Zero%", "Ranking",null,null,null,null,null,null); zero.assignOwner(owner); zero=people.save(zero);
        for(int n=0;n<2;n++) {
            var visit=new org.example.meeter.interactions.Visit("Wizyta ranking",null,java.time.LocalDateTime.of(2026,10,1,12,0),5,java.util.Set.of(zenon,anna,owner.getHuman()),null);
            visit.assignOwner(owner); interactions.save(visit);
        }
        var meeting=new org.example.meeter.interactions.Meeting("Spotkanie ranking",null,java.time.LocalDate.of(2026,10,1),java.util.Set.of(zero,owner.getHuman()),null,null);
        meeting.assignOwner(owner); interactions.save(meeting);
        Human foreign=people.save(new Human("Cudza", "Ranking",null,null,null,null,null,null));
        User other=users.save(new User("rank-other",encoder.encode("other-password-1234"),foreign));
        foreign.assignOwner(other); people.save(foreign);
        var foreignVisit=new org.example.meeter.interactions.Visit("Obca wizyta",null,java.time.LocalDateTime.of(2026,10,1,12,0),1,java.util.Set.of(zero,foreign),null);
        foreignVisit.assignOwner(other); interactions.save(foreignVisit);
        var response=request("GET","/people/browse?q=Ranking&size=1",null);
        assertEquals(200,response.statusCode(),response.body());
        var first=json.readTree(response.body());
        assertEquals(4,first.get("totalElements").asInt()); assertTrue(first.get("hasNext").asBoolean());
        assertEquals(anna.getId().longValue(),first.get("content").get(0).get("person").get("id").asLong());
        assertEquals(2,first.get("content").get(0).get("visitCount").asInt());
        var second=json.readTree(request("GET","/people/browse?q=Ranking&size=1&page=1",null).body());
        assertEquals(zenon.getId().longValue(),second.get("content").get(0).get("person").get("id").asLong());
        var third=json.readTree(request("GET","/people/browse?q=Ranking&size=1&page=2",null).body());
        assertEquals(zero.getId().longValue(),third.get("content").get(0).get("person").get("id").asLong());
        var last=json.readTree(request("GET","/people/browse?q=Ranking&size=1&page=3",null).body());
        assertEquals(0,last.get("content").get(0).get("visitCount").asInt()); assertFalse(last.get("hasNext").asBoolean());
        var unowned=people.save(new Human("Bez właściciela", "Import",null,null,null,null,null,null));
        var imported=json.readTree(request("GET","/people/browse?q=Import",null).body());
        assertEquals(1,imported.get("totalElements").asInt());
        assertEquals(unowned.getId().longValue(),imported.get("content").get(0).get("person").get("id").asLong());
        assertEquals(0,imported.get("content").get(0).get("visitCount").asInt());
        assertEquals(1,json.readTree(request("GET","/people/browse?q=%25",null).body()).get("totalElements").asInt());
        assertEquals(400,request("GET","/people/browse?page=-1",null).statusCode());
        assertEquals(400,request("GET","/people/browse?size=101",null).statusCode());
        var anonymous=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/people/browse")).GET().build(),HttpResponse.BodyHandlers.discarding());
        assertEquals(401,anonymous.statusCode());
    }

    @Test void directoryExcludesCurrentPersonBeforeCountingSearchingAndPagination() throws Exception {
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        var other=people.save(new Human("Test", "Owner",null,null,null,null,null,null));
        var response=request("GET","/people/browse?q=Test%20Owner&size=1",null);
        assertEquals(200,response.statusCode(),response.body());
        var first=json.readTree(response.body());
        assertEquals(1,first.get("totalElements").asInt());
        assertFalse(first.get("hasNext").asBoolean());
        assertEquals(other.getId().longValue(),first.get("content").get(0).get("person").get("id").asLong());
        var second=json.readTree(request("GET","/people/browse?q=Test%20Owner&size=1&page=1",null).body());
        assertEquals(0,second.get("content").size());
        var all=json.readTree(request("GET","/people/browse",null).body());
        assertEquals(1,all.get("totalElements").asInt());
        assertEquals(other.getId().longValue(),all.get("content").get(0).get("person").get("id").asLong());
    }

    @Test void directoryBreaksScoreTiesBySurnameThenFirstNameBeforePagination() throws Exception {
        var zofia=people.save(new Human("Zofia","Aaa Sort",null,null,null,null,null,null));
        var adam=people.save(new Human("Adam","Zzz Sort",null,null,null,null,null,null));
        var anna=people.save(new Human("Anna","Aaa Sort",null,null,null,null,null,null));
        var expected=java.util.List.of(anna.getId(),zofia.getId(),adam.getId());
        for(int page=0;page<3;page++) {
            var response=request("GET","/people/browse?q=Sort&size=1&page="+page,null);
            assertEquals(200,response.statusCode(),response.body());
            var content=json.readTree(response.body());
            assertEquals(3,content.get("totalElements").asInt());
            assertEquals(expected.get(page).longValue(),content.get("content").get(0).get("person").get("id").asLong());
        }
    }

    @Test void directoryExcludesMyExesFromPagesSearchAndPortraitsEvenForAdministrator() throws Exception {
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        var hiddenClique=new Clique(" My_ExEs ",null); hiddenClique.assignOwner(owner); hiddenClique=cliques.save(hiddenClique);
        var hidden=people.save(new Human("Hidden", "Directory",null,null,null,null,null,hiddenClique));
        var visible=people.save(new Human("Visible", "Directory",null,null,null,null,null,null));
        var response=request("GET","/people/browse?q=Directory&size=1",null);
        assertEquals(200,response.statusCode(),response.body());
        var page=json.readTree(response.body()); assertEquals(1,page.get("totalElements").asInt());
        assertEquals(visible.getId().longValue(),page.get("content").get(0).get("person").get("id").asLong());
        assertFalse(page.get("hasNext").asBoolean()); assertFalse(response.body().contains("Hidden"));
        assertEquals(0,json.readTree(request("GET","/people/browse?q=Hidden",null).body()).get("totalElements").asInt());
        assertEquals(404,request("GET","/people/browse/"+hidden.getId()+"/photo",null).statusCode());
    }

    @Test void directoryDrawsOnePublicQuotePerAuthorEvenForAdministrator() throws Exception {
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        var author=people.save(new Human("Publiczny", "Directory",null,null,null,null,null,null));
        var privateAuthor=people.save(new Human("Prywatny", "Directory",null,null,null,null,null,null));
        for(var quote:java.util.List.of(
            new org.example.meeter.quote.Quote("Pierwszy publiczny cytat",author,true),
            new org.example.meeter.quote.Quote("Drugi publiczny cytat",author,true),
            new org.example.meeter.quote.Quote("Tajny cytat autora",author,false),
            new org.example.meeter.quote.Quote("Tylko prywatny cytat",privateAuthor,false),
            new org.example.meeter.quote.Quote("Publiczny bez autora",null,true))) {
            quote.assignOwner(owner); quotes.save(quote);
        }
        for(int n=0;n<3;n++) {
            var response=request("GET","/people/browse?q=Directory",null);
            assertEquals(200,response.statusCode(),response.body());
            var content=json.readTree(response.body()).get("content"); assertEquals(2,content.size());
            for(var item:content) {
                if(item.get("person").get("id").asLong()==author.getId()) {
                    assertTrue(java.util.Set.of("Pierwszy publiczny cytat","Drugi publiczny cytat").contains(item.get("publicQuote").get("quote").asString()));
                    assertNotNull(item.get("publicQuote").get("id"));
                } else assertTrue(item.get("publicQuote").isNull());
            }
            assertFalse(response.body().contains("Tajny")); assertFalse(response.body().contains("Tylko prywatny"));
            assertFalse(response.body().contains("Publiczny bez autora"));
            assertFalse(response.body().contains("\"points\""));
            assertFalse(response.body().contains("socialScore"));
        }
        assertEquals(0,json.readTree(request("GET","/people/browse?q=Directory&page=5",null).body()).get("content").size());
    }

    @Test void ranksPeopleByDecayedPointsAndInvitationsForWeddingCoupleOnly() throws Exception {
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Warsaw"));
        var wags=new Clique(" wAgS ",null); wags.assignOwner(owner); wags=cliques.save(wags);
        var persons=new java.util.HashMap<String,Human>();
        for(String name:java.util.List.of("Recent","Old","Meeting","Event","Trip","Groom","Bride","Guest","Future")) {
            var human=new Human(name,"Score",null,null,null,null,null,name.equals("Groom")?wags:null);
            human.assignOwner(owner); persons.put(name,people.save(human));
        }
        var all=new java.util.ArrayList<org.example.meeter.interactions.Interaction>();
        all.add(new org.example.meeter.interactions.Visit("Recent visit",null,today.atTime(12,0),1,java.util.Set.of(persons.get("Recent")),null));
        all.add(new org.example.meeter.interactions.Visit("Old visit",null,today.minusDays(720).atTime(12,0),10,java.util.Set.of(persons.get("Old")),null));
        all.add(new org.example.meeter.interactions.Meeting("Meeting",null,today.minusDays(360),java.util.Set.of(persons.get("Meeting")),null,null));
        all.add(new org.example.meeter.interactions.Event("Event",null,today.minusDays(181).atStartOfDay(),today.minusDays(179).atStartOfDay(),null,today.minusDays(180).atStartOfDay(),java.util.Set.of(persons.get("Event")),null,null));
        all.add(new org.example.meeter.interactions.Citybreak("Trip",null,today.minusDays(3651).atStartOfDay(),today.minusDays(3650).atStartOfDay(),java.util.Set.of(persons.get("Trip")),java.util.Set.of()));
        var invited=new org.example.meeter.interactions.Wedding("Invited wedding",null,today.minusDays(1825).atTime(12,0),java.util.Set.of(persons.get("Guest")),persons.get("Groom"),persons.get("Bride"),null,null,null,false);
        invited.setWasIInvited(true); all.add(invited);
        all.add(new org.example.meeter.interactions.Wedding("Not invited",null,today.atTime(12,0),java.util.Set.of(persons.get("Guest")),persons.get("Groom"),persons.get("Bride"),null,null,null,false));
        all.add(new org.example.meeter.interactions.Visit("Future visit",null,today.plusDays(1).atTime(12,0),1,java.util.Set.of(persons.get("Future")),null));
        all.add(new org.example.meeter.interactions.Event("First event",null,today.atTime(9,0),today.atTime(10,0),null,null,java.util.Set.of(persons.get("Guest")),null,null));
        all.add(new org.example.meeter.interactions.Event("Second event",null,today.atTime(11,0),today.atTime(12,0),null,null,java.util.Set.of(persons.get("Guest")),null,null));
        all.forEach(item->{item.assignOwner(owner); item.addParticipant(owner.getHuman(),owner); interactions.save(item);});
        var response=request("GET","/people/browse?q=Score&size=100",null);
        assertEquals(200,response.statusCode(),response.body());
        var content=json.readTree(response.body()).get("content");
        assertEquals("Bride Score",content.get(0).get("person").get("fullName").asString());
        var expected=java.util.Map.of("Recent",6.0,"Old",3.0,"Meeting",1.5,"Event",0.5,"Trip",1.5,"Groom",1.5,"Bride",7.5,"Guest",2.0,"Future",0.0);
        assertFalse(response.body().contains("\"points\""));
        var internalScores=new java.util.HashMap<Long,Double>();
        new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query(
            PeopleRanking.SCORES+"select human_id, points from scores",java.util.Map.of("ownerId",owner.getId(),"today",today,"currentHumanId",owner.getHuman().getId()),
            rs->{internalScores.put(rs.getLong("human_id"),rs.getDouble("points"));});
        expected.forEach((name,score)->assertEquals(score,internalScores.getOrDefault(persons.get(name).getId(),0.0),0.00001));
        assertEquals(9,content.size());
        var first=json.readTree(request("GET","/people/browse?q=Score&size=1",null).body());
        assertEquals("Bride Score",first.get("content").get(0).get("person").get("fullName").asString());
        assertEquals(9,first.get("totalElements").asInt()); assertTrue(first.get("hasNext").asBoolean());
        var directory=json.readTree(request("GET","/v2/people/browse?directory=true&q=Score&size=2&page=1",null).body());
        assertEquals(3,directory.get("totalElements").asInt());
        assertFalse(directory.get("hasNext").asBoolean());
        assertEquals(1,directory.get("content").size());
        assertEquals("Old Score",directory.get("content").get(0).get("person").get("fullName").asString());
        assertEquals(0,json.readTree(request("GET","/v2/people/browse?directory=true&q=Guest%20Score",null)
            .body()).get("totalElements").asInt());
    }

    @Test void newsComeFromDatabaseWithStablePaginationAndNoFuturePublications() throws Exception {
        var time=java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Warsaw")).minusDays(1);
        var old=new org.example.meeter.news.NewsEntry("old", "Starsze funkcje", "Pierwszy wpis", time.minusDays(1));
        var first=new org.example.meeter.news.NewsEntry("first", "Nowe funkcje", "Opis zmian", time);
        var latest=new org.example.meeter.news.NewsEntry("latest", "Najnowsze poprawki", "Treść z bazy", time);
        news.saveAndFlush(old); news.saveAndFlush(first); latest=news.saveAndFlush(latest);
        news.saveAndFlush(new org.example.meeter.news.NewsEntry("future", "Nieopublikowane", "Jeszcze nie teraz", time.plusDays(3)));
        var response=request("GET", "/news?size=1", null);
        assertEquals(200,response.statusCode(),response.body());
        var page=json.readTree(response.body());
        assertEquals(3,page.get("totalElements").asInt());
        assertTrue(page.get("hasNext").asBoolean());
        assertEquals(latest.getId().longValue(),page.get("content").get(0).get("id").asLong());
        assertEquals("Treść z bazy",page.get("content").get(0).get("content").asString());
        var last=json.readTree(request("GET","/news?size=1&page=2",null).body());
        assertFalse(last.get("hasNext").asBoolean());
        assertEquals("Starsze funkcje",last.get("content").get(0).get("title").asString());
        assertEquals(400,request("GET","/news?page=-1",null).statusCode());
        assertEquals(400,request("GET","/news?size=101",null).statusCode());
        var anonymous=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/news")).GET().build(),HttpResponse.BodyHandlers.discarding());
        assertEquals(401,anonymous.statusCode());
        news.deleteAll();
        assertEquals(0,json.readTree(request("GET","/news",null).body()).get("totalElements").asInt());
    }

    @Test void createsQuotesWithOptionalAuthorWithoutCollectionEndpoint() throws Exception {
        long author = id(request("POST", "/people", "{\"name\":\"Autor\",\"surname\":\"Cytatu\"}"));
        String text = "Wspomnienie 100%: " + "a".repeat(400);
        var response = request("POST", "/quotes", json.writeValueAsString(java.util.Map.of("quote", "  " + text + "  ", "authorId", author, "isPublic", true)));
        long quoteId = id(response);
        var value = json.readTree(response.body());
        assertEquals(text, value.get("quote").asString());
        assertEquals(author, value.get("author").get("id").asLong());
        assertTrue(value.get("isPublic").asBoolean());
        assertNotNull(value.get("addTime").asString()); assertNotNull(value.get("uuid").asString());
        assertFalse(response.body().contains("createdBy")); assertFalse(response.body().contains("password"));
        assertEquals(owner.getId(), quotes.findById(quoteId).orElseThrow().getCreatedBy().getId());
        var unknown = request("POST", "/quotes", "{\"quote\":\"Autor nieznany\"}");
        assertEquals(400, unknown.statusCode());

        assertEquals(200, request("GET", "/quotes/" + quoteId, null).statusCode());
        assertEquals(405, request("GET", "/quotes", null).statusCode());
    }

    @Test void publicOnlyQuoteModeRestrictsEvenTheAdminAuthor() throws Exception {
        owner.grantRole(Role.ROLE_ADMIN);
        users.saveAndFlush(owner);
        long privateQuote = saveProfileQuote(owner.getHuman(), "Mój prywatny cytat", false);
        long publicQuote = saveProfileQuote(owner.getHuman(), "Mój publiczny cytat", true);
        String path = "/people/browse/" + owner.getHuman().getId() + "/quotes";
        String single = "/quotes/" + privateQuote;
        String plus = "{\"vote\":\"PLUS\"}";
        assertEquals(200, request("GET", single, null).statusCode());
        assertEquals(204, request("PUT", single + "/vote", plus).statusCode());

        var origin = URI.create("http://127.0.0.1:" + port + "/");
        var cookies = ((CookieManager) client.cookieHandler().orElseThrow()).getCookieStore();
        var preference = new java.net.HttpCookie("meeter-public-quotes", "true");
        preference.setPath("/");
        preference.setVersion(0);
        cookies.add(origin, preference);
        assertEquals(404, request("GET", single, null).statusCode());
        assertEquals(404, request("PUT", single + "/vote", plus).statusCode());
        assertEquals(404, request("DELETE", single + "/vote", "{}").statusCode());
        assertEquals(204, request("PUT", "/quotes/" + publicQuote + "/vote", plus).statusCode());
        var page = json.readTree(request("GET", path, null).body());
        assertEquals(1, page.get("totalElements").asInt());
        assertEquals(publicQuote, page.get("content").get(0).get("id").asLong());

        cookies.remove(origin, preference);
        assertEquals(200, request("GET", single, null).statusCode());
        assertEquals(2, json.readTree(request("GET", path, null).body()).get("totalElements").asInt());
    }

    @Test void quoteVotesCanBeRetractedAndRankVisibleQuotesBeforePagination() throws Exception {
        Human author = people.save(new Human("Quote", "Author", null, null, null, null, null, null));
        long best = saveProfileQuote(author, "Starszy publiczny cytat", true);
        long worst = saveProfileQuote(author, "Nowszy publiczny cytat", true);
        long hidden = saveProfileQuote(author, "Prywatny cytat innej osoby", false);
        String plus = "{\"vote\":\"PLUS\"}";
        String minus = "{\"vote\":\"MINUS\"}";

        assertEquals(404, request("PUT", "/quotes/" + hidden + "/vote", plus).statusCode());
        assertEquals(204, request("PUT", "/quotes/" + best + "/vote", plus).statusCode());
        assertEquals(204, request("PUT", "/quotes/" + best + "/vote", plus).statusCode());
        assertEquals(409, request("PUT", "/quotes/" + best + "/vote", minus).statusCode());
        assertEquals(204, request("PUT", "/quotes/" + worst + "/vote", minus).statusCode());

        String path = "/people/browse/" + author.getId() + "/quotes?size=1";
        var firstResponse = request("GET", path, null);
        assertEquals(200, firstResponse.statusCode(), firstResponse.body());
        var firstPage = json.readTree(firstResponse.body());
        assertEquals(2, firstPage.get("totalElements").asInt());
        var first = firstPage.get("content").get(0);
        assertEquals(best, first.get("id").asLong());
        assertEquals(1, first.get("score").asInt());
        assertEquals(1, first.get("myVote").asInt());
        var second = json.readTree(request("GET", path + "&page=1", null).body()).get("content").get(0);
        assertEquals(worst, second.get("id").asLong());
        assertEquals(-1, second.get("score").asInt());

        Human otherHuman = people.save(new Human("Other", "Voter", null, null, null, null, null, null));
        users.save(new User("other-voter", encoder.encode("other-voter-password"), otherHuman));
        assertEquals(200, request("POST", "/auth/login",
            "{\"username\":\"other-voter\",\"password\":\"other-voter-password\"}").statusCode());
        assertEquals(204, request("PUT", "/quotes/" + best + "/vote", minus).statusCode());
        var tied = json.readTree(request("GET", path, null).body()).get("content").get(0);
        assertEquals(best, tied.get("id").asLong());
        assertEquals(0, tied.get("score").asInt());
        assertEquals(-1, tied.get("myVote").asInt());

        assertEquals(404, request("DELETE", "/quotes/" + hidden + "/vote", "{}").statusCode());
        assertEquals(204, request("DELETE", "/quotes/" + best + "/vote", "{}").statusCode());
        assertEquals(204, request("DELETE", "/quotes/" + best + "/vote", "{}").statusCode());
        var retracted = json.readTree(request("GET", path, null).body()).get("content").get(0);
        assertEquals(1, retracted.get("score").asInt());
        assertTrue(retracted.get("myVote").isNull());
        assertEquals(204, request("PUT", "/quotes/" + best + "/vote", plus).statusCode());
    }

    private long saveProfileQuote(Human author, String text, boolean isPublic) {
        var quote = new org.example.meeter.quote.Quote(text, author, isPublic);
        quote.assignOwner(owner);
        return quotes.saveAndFlush(quote).getId();
    }

    @Test void quoteValidationOwnershipAndAuthenticationAreEnforced() throws Exception {
        for (String body : java.util.List.of("{}", "{\"quote\":null}", "{\"quote\":\"abc\"}", "{\"quote\":\"    \"}", "{\"quote\":\" a  \"}", json.writeValueAsString(java.util.Map.of("quote", "x".repeat(10241)))))
            assertEquals(400, request("POST", "/quotes", body).statusCode(), body.substring(0, Math.min(body.length(), 100)));
        assertEquals(400, request("POST", "/quotes", "{\"quote\":\"Tekst cytatu\",\"authorId\":-1}").statusCode());
        assertEquals(404, request("POST", "/quotes", "{\"quote\":\"Tekst cytatu\",\"authorId\":999999}").statusCode());
        Human human = people.save(new Human("Inny", "Autor", null, null, null, null, null, null));
        User other = users.save(new User("quote-other", encoder.encode("other-password-1234"), human));
        human.assignOwner(other); people.save(human);
        var authors = request("GET", "/quotes/authors?q=Inny", null);
        assertEquals(200, authors.statusCode());
        assertEquals(human.getId().longValue(), json.readTree(authors.body()).get(0).get("id").asLong());
        assertTrue(json.readTree(authors.body()).get(0).get("profilePhotoUrl").isNull());
        Human imported = people.save(new Human("Michał", "Importowany", null, null, null, null, null, null));
        var importedAuthors = request("GET", "/quotes/authors?q=micha", null);
        assertEquals(imported.getId().longValue(), json.readTree(importedAuthors.body()).get(0).get("id").asLong());
        assertEquals(201, request("POST", "/quotes", "{\"quote\":\"Tekst cytatu\",\"authorId\":" + human.getId() + "}").statusCode());
        var foreign = new org.example.meeter.quote.Quote("Cudzy prywatny cytat", human, false);
        foreign.assignOwner(other); foreign = quotes.saveAndFlush(foreign);
        assertEquals(404, request("GET", "/quotes/" + foreign.getId(), null).statusCode());
        assertEquals(404, request("GET", "/quotes/999999", null).statusCode());
        var anonymous = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/quotes")).GET().build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(401, anonymous.statusCode());
        var noCsrf = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/quotes")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"quote\":\"Bez tokenu\"}")).build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(403, noCsrf.statusCode()); assertEquals(2, quotes.count());
    }

    @Test void quotesRespectPublicAuthorAndAdministratorVisibility() throws Exception {
        Human human = people.save(new Human("Inna", "Osoba", null, null, null, null, null, null));
        User other = users.save(new User("visibility-other", encoder.encode("other-password-1234"), human));
        human.assignOwner(other); people.save(human);
        var publicQuote = new org.example.meeter.quote.Quote("Publiczna wypowiedź", human, true);
        publicQuote.assignOwner(other); publicQuote = quotes.saveAndFlush(publicQuote);
        var privateQuote = new org.example.meeter.quote.Quote("Prywatna wypowiedź", human, false);
        privateQuote.assignOwner(other); privateQuote = quotes.saveAndFlush(privateQuote);
        var authored = new org.example.meeter.quote.Quote("Moje słowa zapisane przez inną osobę", owner.getHuman(), false);
        authored.assignOwner(other); authored = quotes.saveAndFlush(authored);
        assertEquals(200, request("GET", "/quotes/" + publicQuote.getId(), null).statusCode());
        assertEquals(404, request("GET", "/quotes/" + privateQuote.getId(), null).statusCode());
        assertEquals(200, request("GET", "/quotes/" + authored.getId(), null).statusCode());
        var visible = json.readTree(request("GET", "/quotes/" + publicQuote.getId(), null).body());
        assertTrue(visible.get("author").get("profilePhotoUrl").isNull());
        User admin = users.findById(owner.getId()).orElseThrow(); admin.grantRole(Role.ROLE_ADMIN); users.saveAndFlush(admin);
        var roles = json.readTree(request("GET", "/auth/me", null).body()).get("roles");
        assertTrue(roles.toString().contains("ROLE_USER")); assertTrue(roles.toString().contains("ROLE_ADMIN"));
        assertEquals(200, request("GET", "/quotes/" + privateQuote.getId(), null).statusCode());
    }

    private void seedQuote(String text, Human author, boolean publicQuote) {
        var quote = new org.example.meeter.quote.Quote(text, author, publicQuote);
        quote.assignOwner(owner); quotes.saveAndFlush(quote);
    }

    @Test void quoteGameSingleDrawsPublicForeignAuthorAndFinishesAfterOneAnswer() throws Exception {
        Human author = people.save(new Human("Autor", "Importowany", null, null, null, null, null, null));
        Human sameName = people.save(new Human("Autor", "Importowany", null, null, null, null, null, null));
        seedQuote("Jedyny publiczny cytat z autorem", author, true);
        seedQuote("Prywatny cytat nie trafia do gry", owner.getHuman(), false);
        seedQuote("Publiczny cytat bez autora", null, true);
        var admin = users.findById(owner.getId()).orElseThrow(); admin.grantRole(Role.ROLE_ADMIN); users.saveAndFlush(admin);
        var response = request("POST", "/quote-games/single", "{\"secondsPerQuestion\":120}");
        assertEquals(201, response.statusCode(), response.body());
        var game = json.readTree(response.body());
        long gameId = game.get("summary").get("id").asLong();
        long roundId = game.get("question").get("roundId").asLong();
        assertEquals("SINGLE", game.get("summary").get("scopeType").asText());
        assertEquals(1, game.get("summary").get("total").asInt());
        assertEquals(3, game.get("candidates").size());
        assertEquals("Jedyny publiczny cytat z autorem", game.get("question").get("quote").asText());
        assertNull(game.get("question").get("correctAuthor"));
        assertNull(game.get("question").get("quoteId"));
        var resumed = json.readTree(request("GET", "/quote-games/" + gameId, null).body());
        assertEquals(game.get("question"), resumed.get("question"));
        assertEquals(1, jdbc.queryForObject("select count(*) from quote_game_rounds where game_id=?", Integer.class, gameId));
        var answered = request("POST", "/quote-games/" + gameId + "/guesses", "{\"roundId\":" + roundId + ",\"authorId\":" + sameName.getId() + "}");
        assertEquals(200, answered.statusCode(), answered.body());
        var finished = json.readTree(answered.body());
        assertFalse(finished.get("summary").get("finishedAt").isNull());
        assertTrue(finished.get("question").isNull());
        assertEquals(0, finished.get("summary").get("correct").asInt());
        assertEquals(author.getId().longValue(), finished.get("answers").get(0).get("correctAuthor").get("id").asLong());
        assertEquals(409, request("POST", "/quote-games/" + gameId + "/guesses", "{\"roundId\":" + roundId + ",\"authorId\":" + author.getId() + "}").statusCode());
        assertEquals(0, json.readTree(request("GET", "/quote-games", null).body()).get("content").size());
        assertEquals(1, json.readTree(request("GET", "/quote-games/single/history", null).body()).get("content").size());
        assertEquals("[]", request("GET", "/quote-games/leaderboard", null).body());
        assertEquals(1, json.readTree(request("GET", "/quote-games/single/leaderboard", null).body()).size());
    }

    @Test void quoteGameSingleRejectsEmptyPoolInvalidTimerAndMissingCsrf() throws Exception {
        seedQuote("Prywatny cytat autora", owner.getHuman(), false);
        seedQuote("Publiczny cytat bez autora", null, true);
        assertEquals(400, request("POST", "/quote-games/single", "{}").statusCode());
        assertEquals(400, request("POST", "/quote-games/single", "{\"secondsPerQuestion\":9}").statusCode());
        var noCsrf = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/quote-games/single"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, noCsrf.statusCode());
        assertEquals(0, quoteGames.count());
    }

    @Test void quoteGameSingleStartsWithEmptyHistoryAndLeaderboardDespiteExistingClassicGames() throws Exception {
        Human author = people.save(new Human("Classic", "Author", null, null, null, null, null, null));
        seedQuote("Publiczne słowa klasycznego autora", author, true);
        var visit = new org.example.meeter.interactions.Visit("Klasyczna gra", null,
            java.time.LocalDateTime.of(2026, 10, 20, 18, 0), 1, java.util.List.of(author, owner.getHuman()), null);
        visit.assignOwner(owner); interactions.saveAndFlush(visit);
        finishGameWithCorrectOtherAnswers(visit.getId(), 1);
        assertEquals(1, json.readTree(request("GET", "/quote-games", null).body()).get("content").size());
        assertEquals(1, json.readTree(request("GET", "/quote-games/leaderboard", null).body()).size());
        assertEquals(0, json.readTree(request("GET", "/quote-games/single/history", null).body()).get("content").size());
        assertEquals("[]", request("GET", "/quote-games/single/leaderboard", null).body());
    }

    @Test void quoteGameVisitsIncludeParticipationWithoutBroadeningOrdinaryReads() throws Exception {
        Human guest = people.save(new Human("Guest", "Foreign", null, null, null, null, null, null));
        User other = users.save(new User("visit-other", encoder.encode("other-password-1234"), guest));
        seedQuote("Publiczne słowa uczestnika wizyty", guest, true);
        var date = java.time.LocalDateTime.of(2026, 10, 20, 18, 0);
        var attended = new org.example.meeter.interactions.Visit("Wspólna wizyta", null, date, 1,
            java.util.List.of(owner.getHuman(), guest), null);
        attended.assignOwner(other); interactions.saveAndFlush(attended);
        var unrelated = new org.example.meeter.interactions.Visit("Cudza wizyta", null, date, 1, java.util.List.of(guest), null);
        unrelated.assignOwner(other); interactions.saveAndFlush(unrelated);
        var owned = new org.example.meeter.interactions.Visit("Własna wizyta", null, date.minusDays(1), 1, java.util.List.of(guest), null);
        owned.assignOwner(owner); interactions.saveAndFlush(owned);
        var meeting = new org.example.meeter.interactions.Meeting("Spotkanie", null, date.toLocalDate(), java.util.List.of(owner.getHuman()), null, null);
        meeting.assignOwner(owner); interactions.saveAndFlush(meeting);

        // No administrator bypass, including after the role is granted below.
        for (int pass = 0; pass < 2; pass++) {
            var response = request("GET", "/quote-games/visits?size=1", null);
            assertEquals(200, response.statusCode(), response.body());
            var first = json.readTree(response.body());
            assertEquals(attended.getId().longValue(), first.get("content").get(0).get("id").asLong());
            assertEquals(2, first.get("content").get(0).get("participantCount").asInt());
            assertTrue(first.get("hasNext").asBoolean());
            var second = json.readTree(request("GET", "/quote-games/visits?size=1&page=1", null).body());
            assertEquals(owned.getId().longValue(), second.get("content").get(0).get("id").asLong());
            assertFalse(second.get("hasNext").asBoolean());
            var searched = json.readTree(request("GET", "/quote-games/visits?q=Wsp%C3%B3lna", null).body());
            assertEquals(1, searched.get("content").size());
            assertEquals(0, json.readTree(request("GET", "/quote-games/visits?q=Cudza", null).body()).get("content").size());
            assertEquals(200, request("GET", "/interactions/" + attended.getId(), null).statusCode());
            assertEquals(404, request("POST", "/quote-games", "{\"visitId\":" + unrelated.getId() + "}").statusCode());
            assertEquals(404, request("POST", "/quote-games", "{\"visitId\":" + meeting.getId() + "}").statusCode());
            var created = request("POST", "/quote-games", "{\"visitId\":" + attended.getId() + "}");
            assertEquals(201, created.statusCode(), created.body());
            assertEquals(2, json.readTree(created.body()).get("candidates").size());
            var admin = users.findById(owner.getId()).orElseThrow();
            admin.grantRole(Role.ROLE_ADMIN); users.saveAndFlush(admin);
        }
        var anonymous = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/quote-games/visits")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonymous.statusCode());
    }

    @Test void quoteGameDrawsTwoPublicQuotesPerGuestAndScoresOnlyOtherAuthors() throws Exception {
        long friendId = id(request("POST", "/people", "{\"name\":\"Gość\",\"surname\":\"Imprezy\"}"));
        Human friend = people.findById(friendId).orElseThrow();
        for (int i=0; i<4; i++) {
            seedQuote("Publiczne słowa gościa " + i, friend, true);
            seedQuote("Moje publiczne słowa " + i, owner.getHuman(), true);
            seedQuote("Sekret gościa " + i, friend, false);
        }
        seedQuote("Publiczny cytat bez autora", null, true);
        User admin = users.findById(owner.getId()).orElseThrow(); admin.grantRole(Role.ROLE_ADMIN); users.saveAndFlush(admin);
        long visitId = id(request("POST", "/visits", "{" + details("Wizyta na urodzinach", friendId) + ",\"visitDate\":\"2026-10-20T18:00\",\"duration\":1}"));
        var created = request("POST", "/quote-games", "{\"visitId\":" + visitId + ",\"secondsPerQuestion\":30}");
        assertEquals(201, created.statusCode(), created.body());
        var game = json.readTree(created.body()); long gameId = game.get("summary").get("id").asLong();
        assertEquals(4, game.get("summary").get("total").asInt()); assertEquals(2, game.get("summary").get("eligible").asInt());
        assertEquals(0, game.get("answers").size());
        assertNull(game.get("question").get("correctAuthor")); assertNull(game.get("question").get("quoteId"));
        assertEquals(0, jdbc.queryForObject("select count(*) from quote_game_rounds r join quotes q on q.id=r.quote_id where r.game_id=? and q.is_public=false", Integer.class, gameId));
        assertEquals(2, jdbc.queryForObject("select count(*) from quote_game_rounds where game_id=? and correct_author_id=?", Integer.class, gameId, friendId));
        assertEquals(2, jdbc.queryForObject("select count(*) from quote_game_rounds where game_id=? and correct_author_id=?", Integer.class, gameId, owner.getHuman().getId()));
        long firstRound = game.get("question").get("roundId").asLong();
        assertEquals(400, request("POST", "/quote-games/" + gameId + "/guesses", "{\"roundId\":" + firstRound + ",\"authorId\":999999}").statusCode());
        assertEquals(400, request("POST", "/quote-games/" + gameId + "/guesses", "{\"roundId\":" + firstRound + "}").statusCode());
        boolean expiredFriend = false;
        for (int i=0; i<4; i++) {
            long roundId = game.get("question").get("roundId").asLong();
            Long actual = jdbc.queryForObject("select correct_author_id from quote_game_rounds where id=?", Long.class, roundId);
            boolean self = actual.equals(owner.getHuman().getId());
            long chosen = self ? friendId : actual;
            if (!self && !expiredFriend) {
                jdbc.update("update quote_game_rounds set deadline_at=dateadd('SECOND',-1,CURRENT_TIMESTAMP) where id=?", roundId);
                expiredFriend = true;
            }
            var answered = request("POST", "/quote-games/" + gameId + "/guesses", "{\"roundId\":" + roundId + ",\"authorId\":" + chosen + "}");
            assertEquals(200, answered.statusCode(), answered.body()); game = json.readTree(answered.body());
            assertTrue(game.get("question").isNull());
            assertEquals(409, request("POST", "/quote-games/" + gameId + "/guesses", "{\"roundId\":" + roundId + ",\"authorId\":" + chosen + "}").statusCode());
            if (i<3) {
                var next = request("POST", "/quote-games/" + gameId + "/next", "{}");
                assertEquals(200, next.statusCode(), next.body()); game = json.readTree(next.body());
            }
        }
        var summary = game.get("summary");
        assertEquals(4, summary.get("answered").asInt()); assertEquals(1, summary.get("correct").asInt());
        assertEquals(50.0, summary.get("percentage").asDouble()); assertFalse(summary.get("finishedAt").isNull());
        assertEquals(4, jdbc.queryForObject("select count(*) from quote_guesses where created_by_id=? and created_at is not null and id>0", Integer.class, owner.getId()));
        var board = json.readTree(request("GET", "/quote-games/leaderboard", null).body());
        assertEquals(1, board.size()); assertEquals(50.0, board.get(0).get("percentage").asDouble());
        assertEquals(2, board.get(0).get("eligible").asInt());
        assertEquals(4, json.readTree(request("GET", "/quote-games/" + gameId, null).body()).get("answers").size());
    }

    @Test void quoteGameUsesCliqueUnionRejectsInvalidScopesAndKeepsGamesPrivate() throws Exception {
        long a = id(request("POST", "/cliques", "{\"cliqueName\":\"Ekipa A\"}"));
        long b = id(request("POST", "/cliques", "{\"cliqueName\":\"Ekipa B\"}"));
        long first = id(request("POST", "/people", "{\"name\":\"Pierwszy\",\"cliqueId\":" + a + "}"));
        long second = id(request("POST", "/people", "{\"name\":\"Drugi\",\"cliqueId\":" + b + "}"));
        for (int i=0; i<4; i++) seedQuote("Publiczny cytat pierwszej osoby " + i, people.findById(first).orElseThrow(), true);
        seedQuote("Publiczny cytat drugiej osoby", people.findById(second).orElseThrow(), true);
        seedQuote("Niewidoczny sekret drugiej osoby", people.findById(second).orElseThrow(), false);
        assertEquals(400, request("POST", "/quote-games", "{}").statusCode());
        assertEquals(400, request("POST", "/quote-games", "{\"visitId\":1,\"cliqueIds\":[" + a + "]}").statusCode());
        assertEquals(400, request("POST", "/quote-games", "{\"cliqueIds\":[" + a + "],\"secondsPerQuestion\":9}").statusCode());
        assertEquals(404, request("POST", "/quote-games", "{\"cliqueIds\":[999999]}").statusCode());
        assertEquals(0, quoteGames.count());
        var response = request("POST", "/quote-games", "{\"cliqueIds\":[" + a + "," + a + "," + b + "]}");
        assertEquals(201, response.statusCode(), response.body());
        var game = json.readTree(response.body()); long gameId = game.get("summary").get("id").asLong();
        assertEquals(3, game.get("summary").get("total").asInt()); assertEquals(2, game.get("candidates").size());
        assertEquals(2, jdbc.queryForObject("select count(*) from quote_game_cliques where game_id=?", Integer.class, gameId));
        var deadline = game.get("question").get("deadlineAt");
        assertEquals(deadline, json.readTree(request("GET", "/quote-games/" + gameId, null).body()).get("question").get("deadlineAt"));
        assertEquals(deadline, json.readTree(request("POST", "/quote-games/" + gameId + "/next", "{}").body()).get("question").get("deadlineAt"));
        Human stranger = people.save(new Human("Obcy", "Gracz", null, null, null, null, null, null));
        User other = users.save(new User("game-other", encoder.encode("other-password-1234"), stranger));
        stranger.assignOwner(other); people.save(stranger);
        assertEquals(200, request("POST", "/auth/login", "{\"username\":\"game-other\",\"password\":\"other-password-1234\"}").statusCode());
        assertEquals(404, request("GET", "/quote-games/" + gameId, null).statusCode());
        assertEquals(404, request("POST", "/quote-games/" + gameId + "/next", "{}").statusCode());
        assertEquals(0, json.readTree(request("GET", "/quote-games", null).body()).get("content").size());
        assertEquals(404, request("POST", "/quote-games", "{\"cliqueIds\":[" + a + "]}").statusCode());
    }

    @Test void quoteGameExpirySurvivesResumeAndRetractedPublicQuotesAreHidden() throws Exception {
        long person = id(request("POST", "/people", "{\"name\":\"Autor gry\"}"));
        seedQuote("Publiczny cytat z limitem czasu", people.findById(person).orElseThrow(), true);
        long visit = id(request("POST", "/visits", "{" + details("Wizyta z timerem", person) + ",\"visitDate\":\"2026-10-20T18:00\",\"duration\":1}"));
        var response = request("POST", "/quote-games", "{\"visitId\":" + visit + ",\"secondsPerQuestion\":10}");
        assertEquals(201, response.statusCode(), response.body());
        var game = json.readTree(response.body()); long id = game.get("summary").get("id").asLong();
        long round = game.get("question").get("roundId").asLong();
        jdbc.update("update quote_game_rounds set deadline_at=dateadd('SECOND',-1,CURRENT_TIMESTAMP) where id=?", round);
        var resumed = request("GET", "/quote-games/" + id, null);
        assertEquals(200, resumed.statusCode(), resumed.body()); var result = json.readTree(resumed.body());
        assertTrue(result.get("answers").get(0).get("timedOut").asBoolean());
        assertEquals(0.0, result.get("summary").get("percentage").asDouble());
        assertFalse(result.get("summary").get("finishedAt").isNull());
        assertEquals(200, request("GET", "/quote-games/" + id, null).statusCode());
        assertEquals(1, jdbc.queryForObject("select count(*) from quote_guesses", Integer.class));
        jdbc.update("update quotes set is_public=false");
        assertEquals(404, request("GET", "/quote-games/" + id, null).statusCode());
    }

    @Test void quoteGameWithOnlySelfQuotesDoesNotEnterLeaderboard() throws Exception {
        seedQuote("Moje słowa poza rankingiem", owner.getHuman(), true);
        long visit = id(request("POST", "/visits", "{" + details("Samotna wizyta") + ",\"visitDate\":\"2026-10-20T18:00\",\"duration\":1}"));
        var response = request("POST", "/quote-games", "{\"visitId\":" + visit + "}");
        assertEquals(201, response.statusCode(), response.body()); var game = json.readTree(response.body());
        long id = game.get("summary").get("id").asLong(), round = game.get("question").get("roundId").asLong();
        var answer = request("POST", "/quote-games/" + id + "/guesses", "{\"roundId\":" + round + ",\"authorId\":" + owner.getHuman().getId() + "}");
        assertEquals(200, answer.statusCode(), answer.body());
        assertTrue(json.readTree(answer.body()).get("summary").get("percentage").isNull());
        assertEquals("[]", request("GET", "/quote-games/leaderboard", null).body());
    }

    private long finishGameWithCorrectOtherAnswers(long visitId, int correctAnswers) throws Exception {
        var created = request("POST", "/quote-games", "{\"visitId\":" + visitId + ",\"secondsPerQuestion\":120}");
        assertEquals(201, created.statusCode(), created.body()); var game = json.readTree(created.body());
        long gameId = game.get("summary").get("id").asLong();
        while (game.get("summary").get("finishedAt").isNull()) {
            long round = game.get("question").get("roundId").asLong();
            long actual = jdbc.queryForObject("select correct_author_id from quote_game_rounds where id=?", Long.class, round);
            long chosen = correctAnswers-- > 0 ? actual : owner.getHuman().getId();
            var answer = request("POST", "/quote-games/" + gameId + "/guesses", "{\"roundId\":" + round + ",\"authorId\":" + chosen + "}");
            assertEquals(200, answer.statusCode(), answer.body()); game = json.readTree(answer.body());
            if (game.get("summary").get("finishedAt").isNull()) game = json.readTree(request("POST", "/quote-games/" + gameId + "/next", "{}").body());
        }
        return gameId;
    }

    @Test void quoteLeaderboardRanksPercentagesAndKeepsBestGamePerPlayer() throws Exception {
        Long firstPlayer = owner.getId();
        long firstAuthor = id(request("POST", "/people", "{\"name\":\"Autor pierwszego gracza\"}"));
        seedQuote("Jedyny cytat pierwszej puli", people.findById(firstAuthor).orElseThrow(), true);
        long visit = id(request("POST", "/visits", "{" + details("Pierwsza pula", firstAuthor) + ",\"visitDate\":\"2026-10-20T18:00\",\"duration\":1}"));
        long best = finishGameWithCorrectOtherAnswers(visit, 1);
        finishGameWithCorrectOtherAnswers(visit, 0);
        Human player = people.save(new Human("Drugi", "Gracz", null, null, null, null, null, null));
        User other = users.save(new User("leaderboard-other", encoder.encode("other-password-1234"), player));
        player.assignOwner(other); people.save(player);
        assertEquals(200, request("POST", "/auth/login", "{\"username\":\"leaderboard-other\",\"password\":\"other-password-1234\"}").statusCode());
        owner = other;
        long secondAuthor = id(request("POST", "/people", "{\"name\":\"Autor drugiego gracza\"}"));
        seedQuote("Pierwszy cytat drugiej puli", people.findById(secondAuthor).orElseThrow(), true);
        seedQuote("Drugi cytat drugiej puli", people.findById(secondAuthor).orElseThrow(), true);
        long otherVisit = id(request("POST", "/visits", "{" + details("Druga pula", secondAuthor) + ",\"visitDate\":\"2026-10-20T18:00\",\"duration\":1}"));
        finishGameWithCorrectOtherAnswers(otherVisit, 1);
        var board = json.readTree(request("GET", "/quote-games/leaderboard", null).body());
        assertEquals(2, board.size()); assertEquals(firstPlayer.longValue(), board.get(0).get("playerId").asLong());
        assertEquals(best, board.get(0).get("gameId").asLong()); assertEquals(100.0, board.get(0).get("percentage").asDouble());
        assertEquals(50.0, board.get(1).get("percentage").asDouble());
        assertEquals(1, board.get(0).get("correct").asInt()); assertEquals(1, board.get(1).get("correct").asInt());
        assertEquals(1, board.get(0).get("eligible").asInt()); assertEquals(2, board.get(1).get("eligible").asInt());
    }

    @Test void savesRelationsAndSearchesWithoutExposingEntityCycles() throws Exception {
        long placeId = id(request("POST", "/places", """
            {"name":"Dom","latitude":-90,"longitude":180,"category":"dom"}
            """));
        long cliqueId = id(request("POST", "/cliques", "{\"cliqueName\":\"Przyjaciele\",\"cliqueCapitalId\":" + placeId + "}"));
        var human = request("POST", "/people", "{\"name\":\"Anna\",\"surname\":\"Nowak\",\"homeId\":" + placeId + ",\"cliqueId\":" + cliqueId + "}");
        id(human);
        assertTrue(human.body().contains("\"uuid\""));
        assertTrue(human.body().contains("Przyjaciele"));
        assertTrue(human.body().contains("\"home\""));
        var search = request("GET", "/people?q=ANNA%20NOW", null);
        assertEquals(200, search.statusCode());
        assertTrue(search.body().contains("Anna Nowak"));
        assertEquals("[]", request("GET", "/people?q=missing", null).body());
        assertTrue(request("GET", "/cliques?q=PRZY", null).body().contains("Przyjaciele"));
        assertEquals("[]", request("GET", "/cliques?q=%25", null).body());
    }

    @Test void rejectsInvalidDataAndUnknownRelations() throws Exception {
        for (String json : new String[] {
            "{\"name\":\"X\",\"latitude\":91,\"longitude\":0}",
            "{\"name\":\"X\",\"latitude\":0,\"longitude\":-181}",
            "{\"name\":\"X\",\"longitude\":0}",
            "{\"name\":\"  \",\"latitude\":0,\"longitude\":0}"
        }) assertEquals(400, request("POST", "/places", json).statusCode(), json);
        for (String name : new String[] { "A", "  ", "A ", "a".repeat(33) })
            assertEquals(400, request("POST", "/cliques", "{\"cliqueName\":\"" + name + "\"}").statusCode());
        assertEquals(404, request("POST", "/cliques", "{\"cliqueName\":\"Ekipa\",\"cliqueCapitalId\":99999}").statusCode());
        assertEquals(404, request("POST", "/people", "{\"name\":\"Anna\",\"cliqueId\":99999}").statusCode());
        assertEquals(400, request("POST", "/people", "{\"name\":\"Anna\",\"birthdayDate\":\"2999-01-01\"}").statusCode());
        assertEquals(400, request("POST", "/people", "{\"name\":\"Anna\",\"fbLink\":\"javascript:alert(1)\"}").statusCode());
        assertEquals(400, request("GET", "/people?limit=0", null).statusCode());
        assertEquals(1, people.count());
        assertEquals(0, cliques.count());
        assertEquals(0, places.count());
    }

    @Test void loginIssuesCookieAndServerTokenForOneDay() throws Exception {
        var before=Instant.now();
        var login=request("POST","/auth/login","{\"username\":\""+username+"\",\"password\":\"test-password-123456\"}");
        var after=Instant.now();
        assertEquals(200,login.statusCode(),login.body());
        assertTrue(login.headers().firstValue("set-cookie").orElseThrow().contains("Max-Age=86400"));
        var token=tokens.findAll().getFirst();
        assertFalse(token.getExpiresAt().isBefore(before.plusSeconds(86400)));
        assertFalse(token.getExpiresAt().isAfter(after.plusSeconds(86400)));
        assertEquals(1,tokens.count());
        assertEquals(2,authEvents.count()); // Session replacement retains both successful logins.
    }

    @Test void authHistorySurvivesTokenAndAccountDeletionWithoutStoringCredentials() throws Exception {
        var login=authEvents.findAll().getFirst();
        assertEquals(AuthEvent.Type.LOGIN_SUCCESS,login.getType());
        assertEquals(owner.getId(),login.getAccountId());
        assertEquals(tokens.findAll().getFirst().getId(),login.getSessionRecordId());
        assertNotNull(login.getOccurredAt());
        assertFalse(login.getUserAgent().isBlank());
        String missing="missing-"+UUID.randomUUID();
        var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/login"))
            .header("Content-Type","application/json").header("X-XSRF-TOKEN",csrf)
            .header("User-Agent","Audit-fixture "+"x".repeat(700)).header("X-Forwarded-For","198.51.100.90")
            .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\""+missing+"\",\"password\":\"wrong-secret\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(401,response.statusCode());
        var failure=authEvents.findAll().stream().filter(e->e.getType()==AuthEvent.Type.LOGIN_FAILURE).findFirst().orElseThrow();
        assertNull(failure.getAccountId()); assertNull(failure.getSessionRecordId());
        assertEquals(missing,failure.getUsername());
        assertNotEquals("198.51.100.90",failure.getIpAddress());
        assertEquals(512,failure.getUserAgent().length());
        assertEquals(204,request("POST","/auth/logout","{}").statusCode());
        assertEquals(0,tokens.count());
        var logout=authEvents.findAll().stream().filter(e->e.getType()==AuthEvent.Type.LOGOUT).findFirst().orElseThrow();
        assertEquals(login.getSessionRecordId(),logout.getSessionRecordId());
        assertEquals(401,request("POST","/auth/logout","{}").statusCode());
        assertEquals(3,authEvents.count());
        String history=json.writeValueAsString(jdbc.queryForList("select * from auth_events"));
        assertFalse(history.contains("wrong-secret"));
        assertFalse(history.contains("test-password-123456"));
        assertFalse(history.contains(loginCookie.substring(loginCookie.indexOf('=')+1)));
        assertFalse(history.contains("token_hash"));
        jdbc.update("update people set created_by_id=null where id=?",owner.getHuman().getId());
        users.deleteById(owner.getId());
        assertEquals(3,authEvents.count());
    }

    @Test void failedAuditRollsBackSessionReplacement() throws Exception {
        Long session=tokens.findAll().getFirst().getId();
        jdbc.execute("alter table auth_events add constraint test_reject_auth_audit check (event_type <> 'LOGIN_SUCCESS' or id="+authEvents.findAll().getFirst().getId()+")");
        try {
            assertEquals(409,request("POST","/auth/login","{\"username\":\""+username+"\",\"password\":\"test-password-123456\"}").statusCode());
            assertEquals(1,tokens.count());
            assertTrue(tokens.existsById(session));
            assertEquals(1,authEvents.count());
            assertEquals(200,request("GET","/auth/me",null).statusCode());
        } finally { jdbc.execute("alter table auth_events drop constraint test_reject_auth_audit"); }
    }

    @Test void requiresAuthenticationCsrfAndRevokesLogoutToken() throws Exception {
        var anonymous = HttpClient.newHttpClient();
        var endpoint = URI.create("http://127.0.0.1:" + port + "/api/people");
        assertEquals(401, anonymous.send(HttpRequest.newBuilder(endpoint).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        var unsafe = HttpRequest.newBuilder(endpoint).header("Cookie", loginCookie).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test\"}")).build();
        assertEquals(403, anonymous.send(unsafe, HttpResponse.BodyHandlers.ofString()).statusCode());
        String raw = loginCookie.substring(loginCookie.indexOf('=') + 1);
        assertNotEquals(raw, tokens.findAll().getFirst().getTokenHash());
        assertEquals(AuthService.hash(raw), tokens.findAll().getFirst().getTokenHash());
        assertTrue(request("GET", "/auth/me", null).body().contains(username));
        assertEquals(204, request("POST", "/auth/logout", "{}").statusCode());
        var replay = HttpRequest.newBuilder(endpoint).header("Cookie", loginCookie).GET().build();
        assertEquals(401, anonymous.send(replay, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test void isolatesOwnersAndRejectsExpiredTokens() throws Exception {
        Human human = people.save(new Human("Other", "Owner", null, null, null, null, null, null));
        User other = users.save(new User("other-" + UUID.randomUUID(), encoder.encode("other-password-1234"), human));
        Place secret = new Place("Private", 0, 0, null); secret.assignOwner(other); secret = places.save(secret);
        assertEquals("[]", request("GET", "/places?q=Private", null).body());
        assertEquals(404, request("POST", "/cliques", "{\"cliqueName\":\"Test\",\"cliqueCapitalId\":" + secret.getId() + "}").statusCode());
        String expired = "ab".repeat(32);
        tokens.save(new AuthToken(AuthService.hash(expired), Instant.now().minusSeconds(1), owner));
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/people"))
            .header("Cookie", "meeterAuth=" + expired).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    @Test void changesPasswordAndInvalidatesAllSessions() throws Exception {
        assertEquals(204, request("POST", "/auth/password", "{\"currentPassword\":\"test-password-123456\",\"newPassword\":\"new-test-password-123\"}").statusCode());
        assertEquals(401, request("GET", "/auth/me", null).statusCode());
        assertEquals(0, tokens.count());
        assertEquals(1,authEvents.findAll().stream().filter(e->e.getType()==AuthEvent.Type.PASSWORD_CHANGED).count());
        assertEquals(401, request("POST", "/auth/login", "{\"username\":\"" + username + "\",\"password\":\"test-password-123456\"}").statusCode());
        assertEquals(200, request("POST", "/auth/login", "{\"username\":\"" + username + "\",\"password\":\"new-test-password-123\"}").statusCode());
    }

    @Test void limitsFailedLoginsAndDoesNotDiscloseAccountExistence() throws Exception {
        String missing = "missing-" + UUID.randomUUID();
        var unknown = request("POST", "/auth/login", "{\"username\":\"" + missing + "\",\"password\":\"wrong\"}");
        var wrong = request("POST", "/auth/login", "{\"username\":\"" + username + "\",\"password\":\"wrong\"}");
        assertEquals(401, unknown.statusCode()); assertEquals(401, wrong.statusCode());
        assertTrue(unknown.body().contains("Nieprawidłowy login lub hasło"));
        assertTrue(wrong.body().contains("Nieprawidłowy login lub hasło"));
        for (int i = 0; i < 4; i++) assertEquals(401, request("POST", "/auth/login", "{\"username\":\"" + username + "\",\"password\":\"wrong\"}").statusCode());
        assertEquals(429, request("POST", "/auth/login", "{\"username\":\"" + username + "\",\"password\":\"wrong\"}").statusCode());
        var events=authEvents.findAll();
        assertEquals(6,events.stream().filter(e->e.getType()==AuthEvent.Type.LOGIN_FAILURE).count());
        assertEquals(1,events.stream().filter(e->e.getType()==AuthEvent.Type.LOGIN_THROTTLED).count());
        assertTrue(events.stream().filter(e->e.getType()==AuthEvent.Type.LOGIN_FAILURE && e.getUsername().equals(username))
            .allMatch(e->owner.getId().equals(e.getAccountId())));
    }

    private String details(String name, long... ids) {
        return "\"details\":{\"name\":\"" + name + "\",\"longDesc\":\"Wspomnienie\",\"participantIds\":" + java.util.Arrays.toString(ids) + "}";
    }

    @Test void createsEventWithLocalPhotoAndReusesItWithOwnerChecks() throws Exception {
        long initialFiles = 0;
        if (Files.isDirectory(PHOTO_DIRECTORY.resolve("events"))) {
            try (var files = Files.list(PHOTO_DIRECTORY.resolve("events"))) { initialFiles = files.count(); }
        }
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB), "png", buffer);
        String encoded = java.util.Base64.getEncoder().encodeToString(buffer.toByteArray());
        String base = "{" + details("Koncert ze zdjęciem") + ",\"startDate\":\"2026-10-18T18:00\",\"stopDate\":\"2026-10-18T23:00\"";
        var created = request("POST", "/events", base + ",\"placeString\":\"Scena główna\",\"photoName\":\"Moje zdjęcie\",\"photoData\":\"" + encoded + "\"}");
        long eventId = id(created);
        var details = json.readTree(created.body()).get("details");
        assertEquals("Scena główna", details.get("placeString").asString());
        String photoUrl = details.get("photoUrl").asString();
        var image = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + photoUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, image.statusCode());
        var converted = ImageIO.read(new ByteArrayInputStream(image.body()));
        assertEquals(120, converted.getWidth()); assertEquals(60, converted.getHeight());
        id(request("POST", "/events", base + ",\"photoEventId\":" + eventId + "}"));
        try (var files = Files.list(PHOTO_DIRECTORY.resolve("events"))) { assertEquals(initialFiles + 1, files.count()); }
        var anonymous = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + photoUrl)).GET().build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(401, anonymous.statusCode());
        Human stranger = people.save(new Human("Other", "User", null, null, null, null, null, null));
        User other = users.save(new User("photo-other", encoder.encode("other-password-1234"), stranger));
        stranger.assignOwner(other); people.save(stranger);
        var foreign = new org.example.meeter.interactions.Event("Private event", null, java.time.LocalDateTime.of(2026,10,18,18,0), java.time.LocalDateTime.of(2026,10,18,23,0), null, null, java.util.Set.of(stranger), null, null);
        foreign.assignOwner(other); foreign = interactions.save(foreign);
        assertEquals(404, request("GET", "/events/" + foreign.getId() + "/photo", null).statusCode());
        assertEquals(404, request("POST", "/events", base + ",\"photoEventId\":" + foreign.getId() + "}").statusCode());
    }

    @Test void importsEventPhotoFromLinkAndRejectsInvalidImagesAtomically() throws Exception {
        String base = "{" + details("Event ze zdjęciem") + ",\"startDate\":\"2026-10-18T18:00\",\"stopDate\":\"2026-10-18T23:00\"";
        long count = interactions.count();
        assertEquals(400, request("POST", "/events", base + ",\"photoData\":\"bm90IGFuIGltYWdl\"}").statusCode());
        assertEquals(400, request("POST", "/events", base + ",\"photoData\":\"!invalid!\"}").statusCode());
        assertEquals(400, request("POST", "/events", base + ",\"photoUrl\":\"http://127.0.0.1/private.png\"}").statusCode());
        assertEquals(400, request("POST", "/events", base + ",\"photoUrl\":\"https://example.org/a.png\",\"photoData\":\"YQ==\"}").statusCode());
        assertEquals(count, interactions.count());
        var buffer = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB), "png", buffer);
        when(remotePhotos.download(any())).thenReturn(buffer.toByteArray());
        var created = request("POST", "/events", base + ",\"photoUrl\":\"https://example.org/a.png\"}");
        id(created); assertFalse(json.readTree(created.body()).get("details").get("photoUrl").isNull());
    }

    @Test void relationshipRowsHaveGeneratedIdsServerTimestampsAndAuthenticatedCreators() throws Exception {
        long person = id(request("POST", "/people", "{\"name\":\"Towarzysz\"}"));
        long place = id(request("POST", "/places", "{\"name\":\"Cel podróży\",\"latitude\":52,\"longitude\":21}"));
        Instant before = Instant.now().minusSeconds(1);
        long trip = id(request("POST", "/trips", "{" + details("Podróż audytowana", person, person) + ",\"startDate\":\"2026-10-18T12:00\",\"endDate\":\"2026-10-18T18:00\",\"placeIds\":[" + place + "," + place + "]}"));
        assertEquals(2, jdbc.queryForObject("select count(*) from interaction_human where interaction_id=?", Integer.class, trip));
        assertEquals(1, jdbc.queryForObject("select count(*) from trip_place where trip_id=?", Integer.class, trip));
        for (String table : java.util.List.of("interaction_human", "trip_place")) {
            for (var row : jdbc.queryForList("select id,created_at,created_by_id from " + table)) {
                assertTrue(((Number)row.get("ID")).longValue() > 0);
                assertEquals(owner.getId().longValue(), ((Number)row.get("CREATED_BY_ID")).longValue());
                Instant time = ((java.time.OffsetDateTime)row.get("CREATED_AT")).toInstant();
                assertFalse(time.isBefore(before)); assertFalse(time.isAfter(Instant.now().plusSeconds(1)));
            }
        }
        var original = jdbc.queryForList("select * from interaction_human order by id");
        assertEquals(200, request("GET", "/interactions/" + trip, null).statusCode());
        assertEquals(original, jdbc.queryForList("select * from interaction_human order by id"));
        interactions.deleteById(trip);
        assertEquals(0, jdbc.queryForObject("select count(*) from interaction_human", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from trip_place", Integer.class));
        assertTrue(people.existsById(person)); assertTrue(places.existsById(place));
    }

    @Test void editsParticipantsWithoutReplacingExistingRelationshipMetadata() throws Exception {
        long first = id(request("POST", "/people", "{\"name\":\"Pierwszy\"}"));
        long added = id(request("POST", "/people", "{\"name\":\"Dodany\"}"));
        long meeting = id(request("POST", "/meetings", "{" + details("Edycja uczestników", first) + ",\"date\":\"2025-12-06\"}"));
        var original = jdbc.queryForList("select * from interaction_human where interaction_id=? order by id", meeting);
        Instant before = Instant.now().minusSeconds(1);
        var response = request("POST", "/interactions/" + meeting + "/participants", "{\"participantIds\":[" + first + "," + added + "," + added + "]}");
        assertEquals(200, response.statusCode(), response.body());
        var value = json.readTree(response.body());
        assertTrue(value.get("canAddParticipants").asBoolean());
        assertEquals(3, value.get("participants").size());
        assertEquals(3, value.get("removableParticipantIds").size());
        assertEquals(original, jdbc.queryForList("select * from interaction_human where interaction_id=? and human_id<>? order by id", meeting, added));
        long linkId = jdbc.queryForObject("select id from interaction_human where interaction_id=? and human_id=?", Long.class, meeting, added);
        assertTrue(linkId > 0);
        assertEquals(owner.getId(), jdbc.queryForObject("select created_by_id from interaction_human where id=?", Long.class, linkId));
        var time = jdbc.queryForObject("select created_at from interaction_human where id=?", java.time.OffsetDateTime.class, linkId).toInstant();
        assertFalse(time.isBefore(before)); assertFalse(time.isAfter(Instant.now().plusSeconds(1)));
        assertEquals(200, request("POST", "/interactions/" + meeting + "/participants", "{\"participantIds\":[" + added + "]}").statusCode());
        assertEquals(linkId, jdbc.queryForObject("select id from interaction_human where interaction_id=? and human_id=?", Long.class, meeting, added));
        assertEquals(204, request("DELETE", "/interactions/" + meeting + "/participants/" + added, "").statusCode());
        assertTrue(people.existsById(added));
        assertEquals(original, jdbc.queryForList("select * from interaction_human where interaction_id=? order by id", meeting));
        assertEquals(404, request("DELETE", "/interactions/" + meeting + "/participants/" + added, "").statusCode());
        assertEquals(204, request("DELETE", "/interactions/" + meeting + "/participants/" + owner.getHuman().getId(), "").statusCode());
        assertEquals(200, request("GET", "/interactions/" + meeting, null).statusCode());
        assertEquals(1, json.readTree(request("GET", "/interactions/" + meeting, null).body()).get("participants").size());
        interactions.deleteById(meeting);
        assertEquals(0, jdbc.queryForObject("select count(*) from interaction_human where interaction_id=?", Integer.class, meeting));
        assertTrue(people.existsById(first)); assertTrue(users.existsById(owner.getId()));
    }

    @Test void participantRemovalRequiresSelfOrLinkCreatorAndRevokesForeignInteractionRead() throws Exception {
        Human foreignHuman = people.save(new Human("Inny", "Użytkownik", null, null, null, null, null, null));
        User other = users.save(new User("participant-other", encoder.encode("other-password-1234"), foreignHuman));
        foreignHuman.assignOwner(other); people.save(foreignHuman);
        Human invited = people.save(new Human("Zaproszony", null, null, null, null, null, null, null));
        invited.assignOwner(owner); people.save(invited);
        var meeting = new org.example.meeter.interactions.Meeting("Cudze spotkanie", null, java.time.LocalDate.of(2025, 12, 6), java.util.Set.of(owner.getHuman(), foreignHuman), null, null);
        meeting.assignOwner(other);
        meeting.addParticipant(invited, owner);
        long meetingId = interactions.saveAndFlush(meeting).getId();
        owner.grantRole(Role.ROLE_ADMIN); users.saveAndFlush(owner);
        String path = "/interactions/" + meetingId;
        assertEquals(200, request("GET", path, null).statusCode());
        assertEquals(404, request("POST", path + "/participants", "{\"participantIds\":[" + invited.getId() + "]}").statusCode());
        assertEquals(404, request("DELETE", path + "/participants/" + foreignHuman.getId(), "").statusCode());
        assertEquals(204, request("DELETE", path + "/participants/" + invited.getId(), "").statusCode());
        assertEquals(204, request("DELETE", path + "/participants/" + owner.getHuman().getId(), "").statusCode());
        assertEquals(404, request("GET", path, null).statusCode());
        assertEquals(1, jdbc.queryForObject("select count(*) from interaction_human where interaction_id=?", Integer.class, meetingId));
        assertTrue(people.existsById(invited.getId())); assertTrue(people.existsById(owner.getHuman().getId()));
        assertEquals(404, request("DELETE", "/interactions/999999/participants/" + foreignHuman.getId(), "").statusCode());
        var ownMeeting = new org.example.meeter.interactions.Meeting("Własne spotkanie", null, java.time.LocalDate.of(2025, 12, 7), java.util.Set.of(owner.getHuman()), null, null);
        ownMeeting.assignOwner(owner);
        ownMeeting.addParticipant(invited, other);
        long ownId = interactions.saveAndFlush(ownMeeting).getId();
        var ownView = json.readTree(request("GET", "/interactions/" + ownId, null).body());
        assertTrue(ownView.get("canAddParticipants").asBoolean());
        assertEquals(1, ownView.get("removableParticipantIds").size());
        assertEquals(owner.getHuman().getId().longValue(), ownView.get("removableParticipantIds").get(0).asLong());
        assertEquals(404, request("DELETE", "/interactions/" + ownId + "/participants/" + invited.getId(), "").statusCode());
        assertEquals(2, jdbc.queryForObject("select count(*) from interaction_human where interaction_id=?", Integer.class, ownId));
    }

    @Test void calendarIncludesAttendedForeignInteractionsBeforeFilteringAndPagination() throws Exception {
        Human foreign = people.save(new Human("Other", "Person", null, null, null, null, null, null));
        User creator = users.save(new User("calendar-other", encoder.encode("other-password-1234"), foreign));
        // A namesake must not grant access through name matching.
        Human namesake = people.save(new Human("Test", "Owner", null, null, null, null, null, null));
        var date = java.time.LocalDate.of(2026, 10, 18);
        var shared = new org.example.meeter.interactions.Meeting("Shared calendar", "Shared details", date,
            java.util.Set.of(foreign, owner.getHuman()), null, null);
        shared.assignOwner(creator);
        long sharedId = interactions.saveAndFlush(shared).getId();
        var hidden = new org.example.meeter.interactions.Meeting("Shared private", null, date,
            java.util.Set.of(foreign, namesake), null, null);
        hidden.assignOwner(creator);
        long hiddenId = interactions.saveAndFlush(hidden).getId();
        String filter = "/interactions?from=2026-10-18&to=2026-10-18&type=MEETING&q=Shared&size=1";
        var page = json.readTree(request("GET", filter, null).body());
        assertEquals(1, page.get("totalElements").asLong());
        assertFalse(page.get("hasNext").asBoolean());
        assertEquals(sharedId, page.get("content").get(0).get("id").asLong());
        assertFalse(page.get("content").get(0).get("canAddParticipants").asBoolean());
        assertEquals(200, request("GET", "/interactions/" + sharedId, null).statusCode());
        assertEquals(404, request("GET", "/interactions/" + hiddenId, null).statusCode());
        assertEquals(404, request("PUT", "/meetings/" + sharedId,
            "{\"details\":{\"name\":\"Changed\",\"participantIds\":[]},\"date\":\"2026-10-18\"}").statusCode());
        assertEquals(204, request("DELETE", "/interactions/" + sharedId + "/participants/" + owner.getHuman().getId(), "").statusCode());
        assertEquals(404, request("GET", "/interactions/" + sharedId, null).statusCode());
        assertEquals(0, json.readTree(request("GET", filter, null).body()).get("totalElements").asLong());
        owner.grantRole(Role.ROLE_ADMIN); users.saveAndFlush(owner);
        assertEquals(404, request("GET", "/interactions/" + hiddenId, null).statusCode());
    }

    @Test void participantWritesValidateOwnershipAndRequireAuthenticationAndCsrf() throws Exception {
        long person = id(request("POST", "/people", "{\"name\":\"Znajomy\"}"));
        Human foreignHuman = people.save(new Human("Obcy", null, null, null, null, null, null, null));
        long meeting = id(request("POST", "/meetings", "{" + details("Kontrola uprawnień") + ",\"date\":\"2025-12-06\"}"));
        String path = "/interactions/" + meeting + "/participants";
        assertEquals(404, request("POST", path, "{\"participantIds\":[" + person + ",999999]}").statusCode());
        assertEquals(1, jdbc.queryForObject("select count(*) from interaction_human where interaction_id=?", Integer.class, meeting));
        for (String body : java.util.List.of("{}", "{\"participantIds\":[]}", "{\"participantIds\":[null]}", "{\"participantIds\":[-1]}"))
            assertEquals(400, request("POST", path, body).statusCode());
        assertEquals(400, request("DELETE", path + "/-1", "").statusCode());
        var noCsrf = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path + "/" + owner.getHuman().getId()))
            .DELETE().build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(403, noCsrf.statusCode());
        var anonymous = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"participantIds\":[" + person + "]}")).build(), HttpResponse.BodyHandlers.discarding());
        assertTrue(anonymous.statusCode() == 401 || anonymous.statusCode() == 403);
    }

    @Test void createsAllSixInteractionTypesAndKeepsThemSeparate() throws Exception {
        long place = id(request("POST", "/places", "{\"name\":\"Sala\",\"latitude\":52,\"longitude\":21}"));
        long person = id(request("POST", "/people", "{\"name\":\"Anna\",\"surname\":\"Testowa\"}"));
        var meeting = request("POST", "/meetings", "{" + details("Kawa", person, person) + ",\"date\":\"2026-10-18\",\"placeId\":" + place + ",\"placeString\":\"Stolik przy oknie\"}");
        long meetingId = id(meeting);
        var meetingJson = json.readTree(meeting.body());
        assertEquals("MEETING", meetingJson.get("type").asString());
        assertEquals(2, meetingJson.get("participants").size());
        assertEquals(2, jdbc.queryForObject("select count(*) from interaction_human where interaction_id=?", Integer.class, meetingId));
        UUID.fromString(meetingJson.get("uuid").asString());
        assertEquals("Stolik przy oknie", meetingJson.get("details").get("placeString").asString());

        var visit = request("POST", "/visits", "{" + details("Weekend", person) + ",\"visitDate\":\"2026-10-30T10:00\",\"duration\":4,\"placeId\":" + place + "}");
        long visitId = id(visit);
        assertTrue(json.readTree(visit.body()).get("endDate").asString().startsWith("2026-11-02T10:00"));
        assertEquals(4, json.readTree(visit.body()).get("details").get("duration").asInt());

        var event = request("POST", "/events", "{" + details("Koncert 100%", person) + ",\"startDate\":\"2026-10-18T18:00\",\"stopDate\":\"2026-10-18T23:00\",\"meComingDate\":\"2026-10-18T19:00\",\"meLeavingDate\":\"2026-10-18T22:00\"}");
        id(event);
        assertTrue(json.readTree(event.body()).get("details").get("meComingDate").asString().contains("19:00"));
        id(request("POST", "/trips", "{" + details("Podróż", person) + ",\"startDate\":\"2026-10-01T08:00\",\"endDate\":\"2026-10-05T20:00\",\"placeIds\":[" + place + "," + place + "]}"));
        for (int i = 0; i < 2; i++) {
            id(request("POST", "/flatmates", "{" + details("Wspólne mieszkanie") + ",\"humanId\":" + person + ",\"startDate\":\"2026-01-01\"}"));
            id(request("POST", "/weddings", "{" + details("Ślub", person) + ",\"dateTime\":\"2026-10-17T15:00\",\"groomId\":" + owner.getHuman().getId() + ",\"brideId\":" + person + ",\"ceremonyPlaceId\":" + place + ",\"partyPlaceId\":" + place + ",\"hadAfter\":true}"));
        }
        var all = json.readTree(request("GET", "/interactions?size=2", null).body());
        assertEquals(8, all.get("totalElements").asInt());
        assertTrue(all.get("hasNext").asBoolean());
        var onlyEvents = json.readTree(request("GET", "/interactions?type=EVENT", null).body());
        assertEquals(1, onlyEvents.get("totalElements").asInt());
        assertEquals("EVENT", onlyEvents.get("content").get(0).get("type").asString());
        assertEquals(1, json.readTree(request("GET", "/interactions?q=%25", null).body()).get("totalElements").asInt());
        var november = json.readTree(request("GET", "/interactions?from=2026-11-01&to=2026-11-30&type=VISIT", null).body());
        assertEquals(visitId, november.get("content").get(0).get("id").asLong());
        assertEquals(200, request("GET", "/interactions/" + meetingId, null).statusCode());
        assertEquals(1, json.readTree(request("GET", "/interactions?from=2026-10-03&to=2026-10-03&type=TRIP", null).body()).get("totalElements").asInt());
    }

    @Test void validatesInteractionSpecificDatesRolesAndDuration() throws Exception {
        String base = "{" + details("Test");
        for (int duration : new int[] {0, -1, 3651})
            assertEquals(400, request("POST", "/visits", base + ",\"visitDate\":\"2026-10-18T12:00\",\"duration\":" + duration + "}").statusCode());
        assertEquals(400, request("POST", "/meetings", base + "}").statusCode());
        assertEquals(400, request("POST", "/events", base + ",\"startDate\":\"2026-10-18T18:00\",\"stopDate\":\"2026-10-18T17:00\"}").statusCode());
        assertEquals(400, request("POST", "/events", base + ",\"startDate\":\"2026-10-18T18:00\",\"stopDate\":\"2026-10-18T23:00\",\"meComingDate\":\"2026-10-18T17:00\"}").statusCode());
        assertEquals(400, request("POST", "/events", base + ",\"startDate\":\"2026-10-18T18:00\",\"stopDate\":\"2026-10-18T23:00\",\"meComingDate\":\"2026-10-18T22:00\",\"meLeavingDate\":\"2026-10-18T21:00\"}").statusCode());
        assertEquals(400, request("POST", "/trips", base + ",\"startDate\":\"2026-10-19T18:00\",\"endDate\":\"2026-10-18T18:00\",\"placeIds\":[]}").statusCode());
        assertEquals(400, request("POST", "/flatmates", base + ",\"humanId\":" + owner.getHuman().getId() + ",\"startDate\":\"2026-01-01\"}").statusCode());
        assertEquals(400, request("POST", "/weddings", base + ",\"dateTime\":\"2026-10-18T18:00\",\"groomId\":" + owner.getHuman().getId() + ",\"brideId\":" + owner.getHuman().getId() + "}").statusCode());
        assertEquals(400, request("GET", "/interactions?from=2026-11-01&to=2026-10-01", null).statusCode());
        assertEquals(400, request("GET", "/interactions?size=101", null).statusCode());
        assertEquals(0, interactions.count());
    }

    @Test void interactionReferencesAndReadsRespectOwnerBoundaries() throws Exception {
        Human otherHuman = people.save(new Human("Other", "User", null, null, null, null, null, null));
        User other = users.save(new User("other-" + UUID.randomUUID(), encoder.encode("other-password-1234"), otherHuman));
        otherHuman.assignOwner(other); people.save(otherHuman);
        Place otherPlace = new Place("Private", 1, 1, null); otherPlace.assignOwner(other); otherPlace = places.save(otherPlace);
        String base = "{" + details("Test");
        assertEquals(201, request("POST", "/meetings", "{" + details("Test", otherHuman.getId()) + ",\"date\":\"2026-10-18\"}").statusCode());
        assertEquals(404, request("POST", "/visits", base + ",\"visitDate\":\"2026-10-18T12:00\",\"duration\":1,\"placeId\":" + otherPlace.getId() + "}").statusCode());
        assertEquals(404, request("POST", "/trips", base + ",\"startDate\":\"2026-10-18T12:00\",\"endDate\":\"2026-10-18T20:00\",\"placeIds\":[" + otherPlace.getId() + "]}").statusCode());
        var privateMeeting = new org.example.meeter.interactions.Meeting("Private", null, java.time.LocalDate.of(2026,10,18), java.util.Set.of(otherHuman), otherPlace, null);
        privateMeeting.assignOwner(other); privateMeeting = interactions.save(privateMeeting);
        assertEquals(404, request("GET", "/interactions/" + privateMeeting.getId(), null).statusCode());
        assertEquals(1, json.readTree(request("GET", "/interactions", null).body()).get("totalElements").asInt());
        assertEquals(2, interactions.count());
    }

    @Test void directoryPeopleCanBeFoundAndAddedToEvents() throws Exception {
        Human imported = new Human("Michał", "Importowany", null, null, null, null, null, null);
        imported.setProfilePhotoFile("portrait.jpg"); imported = people.save(imported);
        Human foreign = people.save(new Human("Michał", "Cudzy", null, null, null, null, null, null));
        User other = users.save(new User("participant-directory", encoder.encode("other-password-1234"), foreign));
        foreign.assignOwner(other); people.save(foreign);
        Clique hiddenClique = cliques.save(new Clique(" MY_EXES ", null));
        Human hidden = people.save(new Human("Michał", "Ukryty", null, null, null, null, null, hiddenClique));
        var found = request("GET", "/people/participants?q=Micha", null);
        assertEquals(200, found.statusCode());
        var results = json.readTree(found.body()); assertEquals(3, results.size());
        assertTrue(found.body().contains("/api/people/participants/" + imported.getId() + "/photo"));
        assertEquals("[]", request("GET", "/people?q=Micha", null).body());
        String dates = ",\"startDate\":\"2026-10-24T18:00\",\"stopDate\":\"2026-10-24T20:00\"}";
        long event = id(request("POST", "/events", "{" + details("Wydarzenie", imported.getId()) + dates));
        var added = request("POST", "/interactions/" + event + "/participants", "{\"participantIds\":[" + foreign.getId() + "]}");
        assertEquals(200, added.statusCode(), added.body());
        assertEquals(3, json.readTree(added.body()).get("participants").size());
        assertEquals(owner.getId(), jdbc.queryForObject("select created_by_id from interaction_human where interaction_id=? and human_id=?", Long.class, event, foreign.getId()));
        assertEquals(201, request("POST", "/events", "{" + details("Historyczne wydarzenie", hidden.getId()) + dates).statusCode());
        assertEquals(200, request("POST", "/interactions/" + event + "/participants", "{\"participantIds\":[" + hidden.getId() + "]}").statusCode());
        assertEquals(404, request("GET", "/people/browse/" + hidden.getId(), null).statusCode());
        var portrait = new org.example.meeter.multimedia.photo.Photo();
        portrait.setPhotoName("participant-ex.png");
        portrait.setPhotoType(org.example.meeter.multimedia.photo.PhotoType.PROFILE_PICTURE);
        hidden.setProfilePhoto(photoRecords.save(portrait)); people.save(hidden);
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB), "png", PHOTO_DIRECTORY.resolve("participant-ex.png").toFile());
        assertEquals(200, request("GET", "/people/participants/" + hidden.getId() + "/photo", null).statusCode());
        assertEquals(404, request("GET", "/people/browse/" + hidden.getId() + "/photo", null).statusCode());
        assertEquals(2, interactions.count());
        assertNull(people.findById(imported.getId()).orElseThrow().getCreatedBy());
        assertEquals(401, HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/people/participants")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
    }

    @Test void displaysMigratedProfileMetadataAndRejectsUnsafeNamesAndWrongTypes() throws Exception {
        var photo=new org.example.meeter.multimedia.photo.Photo();
        photo.setPhotoName("zaimportowany portret.png");
        photo.setPhotoType(org.example.meeter.multimedia.photo.PhotoType.PROFILE_PICTURE);
        photo.setLatitude(52.2); photo.setLongitude(21.0);
        photo.setGenerationTime(java.time.LocalDateTime.of(2020,1,2,12,30));
        photo=photoRecords.save(photo);
        Human human=new Human("Migracja", "Profil", null,null,null,null,null,null);
        human.assignOwner(owner); human.setProfilePhoto(photo); human=people.save(human);
        ImageIO.write(new BufferedImage(30,40,BufferedImage.TYPE_INT_RGB),"png",PHOTO_DIRECTORY.resolve(photo.getPhotoName()).toFile());
        String url=json.readTree(request("GET","/people?q=Migracja",null).body()).get(0).get("profilePhotoUrl").asString();
        var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200,response.statusCode());
        assertEquals("image/png",response.headers().firstValue("content-type").orElseThrow());
        assertEquals(30,ImageIO.read(new ByteArrayInputStream(response.body())).getWidth());
        var anonymous=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+url)).GET().build(),HttpResponse.BodyHandlers.discarding());
        assertEquals(401,anonymous.statusCode());
        jdbc.update("update people set created_by_id=null where id=?",human.getId());
        String browseUrl=json.readTree(request("GET","/people/browse?q=Migracja",null).body()).get("content").get(0).get("person").get("profilePhotoUrl").asString();
        assertTrue(browseUrl.startsWith("/api/people/browse/"));
        assertEquals(200,client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+browseUrl)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(401,HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+browseUrl)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(404,request("GET","/people/"+human.getId()+"/photo",null).statusCode());
        var visit=new org.example.meeter.interactions.Visit("Migrated visit",null,
            java.time.LocalDateTime.of(2026,10,1,12,0),1,java.util.Set.of(human),null);
        visit.assignOwner(owner); visit=interactions.save(visit);
        var calendar=json.readTree(request("GET","/interactions?from=2026-10-01&to=2026-10-31",null).body());
        String participantUrl=calendar.get("content").get(0).get("participants").get(0).get("profilePhotoUrl").asString();
        assertEquals(browseUrl,participantUrl);
        var participantPhoto=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+participantUrl)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200,participantPhoto.statusCode());
        assertEquals(30,ImageIO.read(new ByteArrayInputStream(participantPhoto.body())).getWidth());
        jdbc.update("update people set created_by_id=? where id=?",owner.getId(),human.getId());
        photo.setPhotoName("../outside.png"); photoRecords.save(photo);
        assertEquals(404,request("GET","/people/"+human.getId()+"/photo",null).statusCode());
        photo.setPhotoName("zaimportowany portret.png");
        for(var type:org.example.meeter.multimedia.photo.PhotoType.values()) {
            if(type==org.example.meeter.multimedia.photo.PhotoType.PROFILE_PICTURE) continue;
            photo.setPhotoType(type); photoRecords.save(photo);
            assertEquals(type,photoRecords.findById(photo.getId()).orElseThrow().getPhotoType());
            assertEquals(404,request("GET","/people/"+human.getId()+"/photo",null).statusCode());
        }
    }

    @Test void importsLocalJpegAndReplacesPhotoWithoutLosingPreviousOnFailure() throws Exception {
        BufferedImage source=new BufferedImage(64,96,BufferedImage.TYPE_INT_RGB);
        var pixels=source.createGraphics(); pixels.setColor(java.awt.Color.ORANGE); pixels.fillRect(0,0,64,96); pixels.dispose();
        var buffer=new ByteArrayOutputStream(); ImageIO.write(source,"png",buffer);
        when(remotePhotos.download(any())).thenReturn(buffer.toByteArray());
        var created=request("POST","/people","{\"name\":\"Osoba ze zdjęciem\",\"profilePhotoUrl\":\"https://example.org/portrait.png\"}");
        long id=id(created);
        assertEquals(1,photoRecords.count());
        assertEquals(org.example.meeter.multimedia.photo.PhotoType.PROFILE_PICTURE,photoRecords.findAll().get(0).getPhotoType());
        String photoUrl=json.readTree(created.body()).get("profilePhotoUrl").asString();
        assertTrue(photoUrl.startsWith("/api/people/"+id+"/photo?v="));
        var image=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+photoUrl)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200,image.statusCode()); assertEquals("image/jpeg",image.headers().firstValue("content-type").orElseThrow());
        BufferedImage converted=ImageIO.read(new ByteArrayInputStream(image.body()));
        assertEquals(512,converted.getWidth()); assertEquals(512,converted.getHeight());
        String old=people.findById(id).orElseThrow().getProfilePhotoFile();
        assertEquals(200,request("POST","/people/"+id+"/photo","{\"url\":\"https://example.org/other.png\"}").statusCode());
        String replacement=people.findById(id).orElseThrow().getProfilePhotoFile();
        assertNotEquals(old,replacement); assertFalse(Files.exists(PHOTO_DIRECTORY.resolve(old)));
        assertTrue(Files.isRegularFile(PHOTO_DIRECTORY.resolve(replacement)));
        when(remotePhotos.download(any())).thenReturn("<html>not an image</html>".getBytes());
        assertEquals(400,request("POST","/people/"+id+"/photo","{\"url\":\"https://example.org/bad.jpg\"}").statusCode());
        assertEquals(replacement,people.findById(id).orElseThrow().getProfilePhotoFile());
        assertTrue(Files.isRegularFile(PHOTO_DIRECTORY.resolve(replacement)));
        assertEquals(400,request("POST","/people/"+id+"/photo","{\"url\":\"http://127.0.0.1/private\"}").statusCode());
        when(remotePhotos.download(any())).thenReturn(new byte[RemotePhotoClient.MAX_BYTES+1]);
        assertEquals(413,request("POST","/people/"+id+"/photo","{\"url\":\"https://example.org/large.jpg\"}").statusCode());
        assertEquals(1,request("GET","/people?q=zdj",null).body().split("profilePhotoUrl",-1).length-1);
    }

    @Test void photoEndpointsRespectOwnershipAndDoNotSavePersonOnImportFailure() throws Exception {
        Human otherHuman=people.save(new Human("Inny","Użytkownik",null,null,null,null,null,null));
        User other=users.save(new User("other-"+UUID.randomUUID(),encoder.encode("other-password-1234"),otherHuman));
        otherHuman.assignOwner(other); people.save(otherHuman);
        assertEquals(404,request("GET","/people/"+otherHuman.getId()+"/photo",null).statusCode());
        assertEquals(404,request("POST","/people/"+otherHuman.getId()+"/photo","{\"url\":\"https://example.org/image.jpg\"}").statusCode());
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        assertTrue(json.readTree(request("GET","/people/browse/"+otherHuman.getId(),null).body()).get("canEditPhoto").asBoolean());
        var output=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"png",output);
        when(remotePhotos.download(any())).thenReturn(output.toByteArray());
        assertEquals(200,request("POST","/people/"+otherHuman.getId()+"/photo","{\"url\":\"https://example.org/image.jpg\"}").statusCode());
        assertEquals(other.getId(),people.findById(otherHuman.getId()).orElseThrow().getCreatedBy().getId());
        when(remotePhotos.download(any())).thenReturn(new byte[]{1,2,3});
        long before=people.count();
        assertEquals(400,request("POST","/people","{\"name\":\"Błędne zdjęcie\",\"profilePhotoUrl\":\"https://example.org/image.jpg\"}").statusCode());
        assertEquals(before,people.count());
    }
}
