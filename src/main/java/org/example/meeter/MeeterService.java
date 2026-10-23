package org.example.meeter;
import static org.example.meeter.ApiModels.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.example.meeter.auth.CurrentAccount;
import org.example.meeter.auth.UserRepository;
import org.example.meeter.photos.PhotoStorage;
@Service @Transactional(readOnly=true)
public class MeeterService {
 private final PlaceRepository places; private final CliqueRepository cliques; private final HumanRepository people;
 private final CurrentAccount account;
 private final UserRepository users;
 private final PhotoStorage photos;
 private final PeopleRanking ranking;
 public MeeterService(PlaceRepository p,CliqueRepository c,HumanRepository h,CurrentAccount account,UserRepository users,PhotoStorage photos,PeopleRanking ranking) { places=p; cliques=c; people=h; this.account=account; this.users=users; this.photos=photos; this.ranking=ranking; }
 private <T> Specification<T> owned() { Long id=account.id(); return (root,q,cb)->cb.equal(root.get("createdBy").get("id"),id); }
 private Place place(Long id) { return id==null?null:places.findOne(this.<Place>owned().and((root,q,cb)->cb.equal(root.get("id"),id))).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Nie znaleziono miejsca.")); }
 private Clique clique(Long id) { return id==null?null:cliques.findOne(this.<Clique>owned().and((root,q,cb)->cb.equal(root.get("id"),id))).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Nie znaleziono kliki.")); }
 @Transactional public PlaceView addPlace(PlaceInput i) {
  if(!Double.isFinite(i.latitude())||!Double.isFinite(i.longitude())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nieprawidłowe współrzędne.");
  Place entity=new Place(i.name().trim(),i.latitude(),i.longitude(),clean(i.category()));
  entity.assignOwner(users.getReferenceById(account.id()));
  return PlaceView.of(places.save(entity));
 }
 @Transactional public CliqueView addClique(CliqueInput i) {
  if(i.cliqueName().trim().length()<2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nazwa kliki musi mieć co najmniej 2 znaki.");
  Clique entity=new Clique(i.cliqueName().trim(),place(i.cliqueCapitalId()));
  entity.assignOwner(users.getReferenceById(account.id()));
  return CliqueView.of(cliques.save(entity));
 }
 @Transactional public HumanView addHuman(HumanInput i) {
  Human entity=new Human(i.name().trim(),clean(i.surname()),i.birthdayDate(),clean(i.fbLink()),clean(i.gender()),clean(i.livesIn()),place(i.homeId()),clique(i.cliqueId()));
  entity.assignOwner(users.getReferenceById(account.id()));
  if(i.profilePhotoUrl()!=null && !i.profilePhotoUrl().isBlank()) photos.importPhoto(entity,i.profilePhotoUrl());
  return HumanView.of(people.save(entity));
 }
 public Human ownHuman(Long id) {
  return people.findOne(this.<Human>owned().and((root,q,cb)->cb.equal(root.get("id"),id))).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Nie znaleziono osoby."));
 }
 public boolean canEditPhoto(Human human) {
  return account.isAdmin() || human.getCreatedBy()!=null && human.getCreatedBy().getId().equals(account.id());
 }
 public PersonProfileView profile(Long id) {
  Human human=browseHuman(id);
  // Build the complete DTO while lazy home/clique/capital relations are available.
  return new PersonProfileView(HumanView.forBrowse(human),canEditPhoto(human));
 }
 private Human photoHuman(Long id) {
  if(!account.isAdmin()) return ownHuman(id);
  return people.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Nie znaleziono osoby."));
 }
 private HumanView updatedPhotoView(Human human) {
  return human.getCreatedBy()!=null && human.getCreatedBy().getId().equals(account.id()) ? HumanView.of(human) : HumanView.forBrowse(human);
 }
 @Transactional public HumanView updatePhoto(Long id,String url) {
  Human human=photoHuman(id); photos.importPhoto(human,url); return updatedPhotoView(human);
 }
 @Transactional public HumanView updatePhotoBytes(Long id,byte[] bytes) {
  Human human=photoHuman(id); photos.importBytes(human,bytes); return updatedPhotoView(human);
 }
 public List<PlaceView> places(String q,int limit) { return places.findAll(this.<Place>owned().and(search(q,"name")),PageRequest.of(0,limit,Sort.by("name","id"))).map(PlaceView::of).getContent(); }
 public List<CliqueView> cliques(String q,int limit) { return cliques.findAll(this.<Clique>owned().and(search(q,"cliqueName")),PageRequest.of(0,limit,Sort.by("cliqueName","id"))).map(CliqueView::of).getContent(); }
 public List<HumanView> people(String query,int limit) {
  Specification<Human> filter=(root,q,cb)->cb.like(cb.lower(cb.concat(cb.concat(root.get("name")," "),cb.coalesce(root.get("surname"),""))),pattern(query),'\\');
  return people.findAll(this.<Human>owned().and(filter),PageRequest.of(0,limit,Sort.by("name","id"))).map(HumanView::of).getContent();
 }
 public PeoplePage browsePeople(String query,int page,int size) {
  return ranking.browse(query,page,size);
 }
 public Human browseHuman(Long id) {
  account.id();
  return people.findById(id).filter(h->h.getClique()==null || !"my_exes".equalsIgnoreCase(h.getClique().getCliqueName().trim()))
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Nie znaleziono osoby."));
 }
 private static <T> Specification<T> search(String query,String field) { return (root,q,cb)->cb.like(cb.lower(root.get(field)),pattern(query),'\\'); }
 private static String pattern(String value) { return "%"+value.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; }
 private static String clean(String value) { return value==null||value.isBlank()?null:value.trim(); }
}
