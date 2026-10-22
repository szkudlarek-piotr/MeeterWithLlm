package org.example.meeter;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="cliques") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Clique extends OwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=32) private String cliqueName;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="capital_id") private Place cliqueCapital;
 public Clique(String name,Place capital) { cliqueName=name; cliqueCapital=capital; }
}
