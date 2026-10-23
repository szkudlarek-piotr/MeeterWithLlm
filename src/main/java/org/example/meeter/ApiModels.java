package org.example.meeter;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
public final class ApiModels {
 private ApiModels() {}
 public record PublicQuoteView(Long id,String quote) {}
 public record PersonProfileView(HumanView person,boolean canEditPhoto) {}
 public record PersonBrowseView(HumanView person,long visitCount,PublicQuoteView publicQuote) {}
 public record PeoplePage(java.util.List<PersonBrowseView> content,int page,int size,long totalElements,boolean hasNext) {}
 public record PlaceInput(@NotBlank @Size(max=200) String name,
  @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
  @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,@Size(max=100) String category) {}
 public record CliqueInput(@NotBlank @Size(min=2,max=32) String cliqueName,@Positive Long cliqueCapitalId) {}
 public record HumanInput(@NotBlank @Size(max=100) String name,@Size(max=100) String surname,
  @PastOrPresent LocalDate birthdayDate,
  @Size(max=2048) @Pattern(regexp="(?i)^https?://[^\\s]+$",message="Podaj adres HTTP lub HTTPS.") String fbLink,
  @Size(max=1) String gender,@Size(max=200) String livesIn,@Positive Long homeId,@Positive Long cliqueId,
  @Size(max=2048) String profilePhotoUrl) {}
 public record PlaceView(Long id,String name,double latitude,double longitude,String category) {
  static PlaceView of(Place p) { return p==null?null:new PlaceView(p.getId(),p.getName(),p.getLatitude(),p.getLongitude(),p.getCategory()); }
 }
 public record CliqueView(Long id,String cliqueName,PlaceView cliqueCapital) {
  static CliqueView of(Clique c) { return c==null?null:new CliqueView(c.getId(),c.getCliqueName(),PlaceView.of(c.getCliqueCapital())); }
 }
 public record HumanView(Long id,UUID uuid,String name,String surname,String fullName,LocalDate birthdayDate,String fbLink,String gender,String livesIn,PlaceView home,CliqueView clique,String profilePhotoUrl,boolean demo) {
  static HumanView forBrowse(Human h) {
   var value=of(h);
   return new HumanView(value.id(),value.uuid(),value.name(),value.surname(),value.fullName(),value.birthdayDate(),value.fbLink(),value.gender(),value.livesIn(),value.home(),value.clique(),value.profilePhotoUrl()==null?null:value.profilePhotoUrl().replace("/api/people/","/api/people/browse/"),value.demo());
  }
  static HumanView of(Human h) { return new HumanView(h.getId(),h.getUuid(),h.getName(),h.getSurname(),h.getFullName(),h.getBirthdayDate(),h.getFbLink(),h.getGender(),h.getLivesIn(),PlaceView.of(h.getHome()),CliqueView.of(h.getClique()),h.getProfilePhotoUrl(),h.isDemo()); }
 }
}
