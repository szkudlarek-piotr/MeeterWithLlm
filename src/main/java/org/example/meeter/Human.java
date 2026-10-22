package org.example.meeter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="people") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Human extends OwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,updatable=false) private UUID uuid=UUID.randomUUID();
 @Column(nullable=false,length=100) private String name;
 @Column(length=100) private String surname;
 private LocalDate birthdayDate;
 @Column(length=2048) private String fbLink;
 @Column(length=1) private String gender;
 @Column(length=200) private String livesIn;
 @Column(length=100) private String profilePhotoFile;
 @OneToOne
 @JoinColumn(name="profile_photo_id")
 @Setter private org.example.meeter.multimedia.photo.Photo profilePhoto;
 @Column(nullable=false, columnDefinition="boolean default false") private boolean demo;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="home_id") private Place home;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="clique_id") private Clique clique;
 public Human(String name,String surname,LocalDate birthdayDate,String fbLink,String gender,String livesIn,Place home,Clique clique) {
 this.name=name; this.surname=surname; this.birthdayDate=birthdayDate; this.fbLink=fbLink; this.gender=gender; this.livesIn=livesIn; this.home=home; this.clique=clique;
 }
 public String getFullName() { return (name+" "+(surname==null?"":surname)).trim(); }
 public void setProfilePhotoFile(String file) { this.profilePhotoFile=file; }
 public void markAsDemo() { demo=true; }
 public String getProfilePhotoUrl() {
  if(profilePhoto!=null) {
   if(profilePhoto.getPhotoType()!=org.example.meeter.multimedia.photo.PhotoType.PROFILE_PICTURE) return null;
   return "/api/people/"+id+"/photo?v="+profilePhoto.getUuid();
  }
  return profilePhotoFile==null ? null : "/api/people/"+id+"/photo?v="+profilePhotoFile;
 }
}
