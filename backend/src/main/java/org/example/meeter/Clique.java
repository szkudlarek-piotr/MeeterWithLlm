package org.example.meeter;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="cliques") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Clique extends OwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,updatable=false) private java.util.UUID uuid=java.util.UUID.randomUUID();
 @Column(nullable=false,length=32) private String cliqueName;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="capital_id") private Place cliqueCapital;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="photo_id")
 @Setter private org.example.meeter.multimedia.photo.Photo photo;
 public Clique(String name,Place capital) { cliqueName=name; cliqueCapital=capital; }
}
