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
    @Autowired org.example.meeter.news.NewsRepository news;
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
    private static final Path PHOTO_DIRECTORY=createPhotoDirectory();
    private static Path createPhotoDirectory() {
        try { return Files.createTempDirectory("meeter-photo-tests-"); }
        catch(IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }
    @DynamicPropertySource static void photos(DynamicPropertyRegistry properties) {
        properties.add("app.photos.directory",()->PHOTO_DIRECTORY.toString());
        properties.add("app.photos.events-directory",()->PHOTO_DIRECTORY.resolve("events").toString());
        properties.add("app.photos.place-categories-directory",()->PHOTO_DIRECTORY.resolve("categories").toString());
    }

    @BeforeEach void clean() throws Exception {
        news.deleteAll();
        quoteGames.deleteAll();
        quotes.deleteAll();
        interactions.deleteAll();
        tokens.deleteAll();
        for (String table : new String[] {"people", "places", "cliques"}) jdbc.update("update " + table + " set created_by_id=null");
        users.deleteAll(); people.deleteAll(); photoRecords.deleteAll(); cliques.deleteAll(); places.deleteAll();
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
        assertEquals(404,request("GET","/interactions/"+visible.getId(),null).statusCode());
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
        owner.grantRole(Role.ROLE_ADMIN); users.save(owner);
        assertTrue(json.readTree(request("GET","/people/browse/"+foreign.getId(),null).body()).get("canEditPhoto").asBoolean());
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
            var visit=new org.example.meeter.interactions.Visit("Wizyta ranking",null,java.time.LocalDateTime.of(2026,10,1,12,0),5,java.util.Set.of(zenon,anna),null);
            visit.assignOwner(owner); interactions.save(visit);
        }
        var meeting=new org.example.meeter.interactions.Meeting("Spotkanie ranking",null,java.time.LocalDate.of(2026,10,1),java.util.Set.of(zero),null,null);
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
        all.forEach(item->{item.assignOwner(owner); interactions.save(item);});
        var response=request("GET","/people/browse?q=Score&size=100",null);
        assertEquals(200,response.statusCode(),response.body());
        var content=json.readTree(response.body()).get("content");
        assertEquals("Bride Score",content.get(0).get("person").get("fullName").asString());
        var expected=java.util.Map.of("Recent",6.0,"Old",3.0,"Meeting",1.5,"Event",0.5,"Trip",1.5,"Groom",1.5,"Bride",7.5,"Guest",0.0,"Future",0.0);
        assertFalse(response.body().contains("\"points\""));
        var internalScores=new java.util.HashMap<Long,Double>();
        new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query(
            PeopleRanking.SCORES+"select human_id, points from scores",java.util.Map.of("ownerId",owner.getId(),"today",today),
            rs->{internalScores.put(rs.getLong("human_id"),rs.getDouble("points"));});
        expected.forEach((name,score)->assertEquals(score,internalScores.getOrDefault(persons.get(name).getId(),0.0),0.00001));
        assertEquals(9,content.size());
        var first=json.readTree(request("GET","/people/browse?q=Score&size=1",null).body());
        assertEquals("Bride Score",first.get("content").get(0).get("person").get("fullName").asString());
        assertEquals(9,first.get("totalElements").asInt()); assertTrue(first.get("hasNext").asBoolean());
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

    @Test void participantRemovalRequiresSelfOrLinkCreatorAndDoesNotExposeForeignInteraction() throws Exception {
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
        assertEquals(404, request("GET", path, null).statusCode());
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

    @Test void participantWritesValidateOwnershipAndRequireAuthenticationAndCsrf() throws Exception {
        long person = id(request("POST", "/people", "{\"name\":\"Znajomy\"}"));
        Human foreignHuman = people.save(new Human("Obcy", null, null, null, null, null, null, null));
        long meeting = id(request("POST", "/meetings", "{" + details("Kontrola uprawnień") + ",\"date\":\"2025-12-06\"}"));
        String path = "/interactions/" + meeting + "/participants";
        assertEquals(404, request("POST", path, "{\"participantIds\":[" + person + "," + foreignHuman.getId() + "]}").statusCode());
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
        assertEquals(404, request("POST", "/meetings", "{" + details("Test", otherHuman.getId()) + ",\"date\":\"2026-10-18\"}").statusCode());
        assertEquals(404, request("POST", "/visits", base + ",\"visitDate\":\"2026-10-18T12:00\",\"duration\":1,\"placeId\":" + otherPlace.getId() + "}").statusCode());
        assertEquals(404, request("POST", "/trips", base + ",\"startDate\":\"2026-10-18T12:00\",\"endDate\":\"2026-10-18T20:00\",\"placeIds\":[" + otherPlace.getId() + "]}").statusCode());
        var privateMeeting = new org.example.meeter.interactions.Meeting("Private", null, java.time.LocalDate.of(2026,10,18), java.util.Set.of(otherHuman), otherPlace, null);
        privateMeeting.assignOwner(other); privateMeeting = interactions.save(privateMeeting);
        assertEquals(404, request("GET", "/interactions/" + privateMeeting.getId(), null).statusCode());
        assertEquals(0, json.readTree(request("GET", "/interactions", null).body()).get("totalElements").asInt());
        assertEquals(1, interactions.count());
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
