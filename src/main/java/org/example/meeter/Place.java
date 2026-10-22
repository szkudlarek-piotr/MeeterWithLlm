package org.example.meeter;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="places") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Place extends OwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, length=200) private String name;
 @Column(nullable=false) private double latitude;
 @Column(nullable=false) private double longitude;
 @Column(length=100) private String category;
 public Place(String name,double latitude,double longitude,String category) { this.name=name; this.latitude=latitude; this.longitude=longitude; this.category=category; }
}
