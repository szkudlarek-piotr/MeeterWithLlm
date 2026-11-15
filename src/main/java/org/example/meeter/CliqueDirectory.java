package org.example.meeter;

import java.util.List;
import org.example.meeter.auth.CurrentAccount;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class CliqueDirectory {
    private final CliqueRepository cliques;
    private final PeopleRanking ranking;
    private final CurrentAccount account;
    public CliqueDirectory(CliqueRepository cliques,PeopleRanking ranking,CurrentAccount account) {
        this.cliques=cliques; this.ranking=ranking; this.account=account;
    }
    public record Card(Long id,String cliqueName,String photoUrl,List<ApiModels.HumanView> members) {}
    public record Page(List<Card> content,int page,int size,long totalElements,boolean hasNext) {}
    public Clique ownClique(Long id) {
        Long owner=account.id();
        return cliques.findOne((root,q,cb)->cb.and(cb.equal(root.get("id"),id),cb.equal(root.get("createdBy").get("id"),owner)))
            .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Nie znaleziono kliki."));
    }
    public Page browse(int page,int size) {
        Long owner=account.id();
        Specification<Clique> filter=(root,q,cb)->cb.and(cb.equal(root.get("createdBy").get("id"),owner),
            cb.notEqual(cb.lower(cb.trim(root.get("cliqueName"))),"my_exes"));
        long total=cliques.count(filter);
        var ids=ranking.rankedCliqueIds(page,size);
        var byId=new java.util.HashMap<Long,Clique>();
        cliques.findAllById(ids).forEach(c->byId.put(c.getId(),c));
        var groups=ids.stream().map(byId::get).toList();
        var members=ranking.cliqueMembers(ids);
        return new Page(groups.stream().map(c->new Card(c.getId(),c.getCliqueName(),
            c.getPhoto()==null?null:"/api/cliques/"+c.getId()+"/photo?v="+c.getPhoto().getUuid(),members.getOrDefault(c.getId(),List.of()))).toList(),
            page,size,total,(long)page*size+size<total);
    }
    public String photoFilename(Long id) {
        var clique=ownClique(id);
        if(clique.getPhoto()==null || clique.getPhoto().getPhotoType()!=org.example.meeter.multimedia.photo.PhotoType.CLIQUE)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Nie znaleziono zdjęcia.");
        return clique.getPhoto().getPhotoName();
    }
}
