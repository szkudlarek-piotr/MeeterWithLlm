package org.example.meeter.interactions;

import java.util.*;
import org.example.meeter.HumanRepository;
import org.example.meeter.auth.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Explicit administrator-only maintenance, never invoked by startup or migrations. */
@Service @Transactional(readOnly=true)
public class ParticipationBackfill {
    private final NamedParameterJdbcTemplate jdbc;
    private final InteractionRepository interactions;
    private final HumanRepository humans;
    private final UserRepository users;
    private final CurrentAccount account;
    public ParticipationBackfill(NamedParameterJdbcTemplate jdbc,InteractionRepository interactions,
            HumanRepository humans,UserRepository users,CurrentAccount account) {
        this.jdbc=jdbc; this.interactions=interactions; this.humans=humans; this.users=users; this.account=account;
    }
    private static final String TYPES="""
        from interactions i
        left join meetings m on m.id=i.id left join visits v on v.id=i.id
        left join events e on e.id=i.id left join trips t on t.id=i.id
        left join weddings w on w.id=i.id
        """;
    private static final String MISSING="not exists (select 1 from interaction_human p where p.interaction_id=i.id and p.human_id=:self)";
    private static final String ELIGIBLE="(m.id is not null or v.id is not null or e.id is not null or t.id is not null or (w.id is not null and w.was_i_invited=true))";
    public record Preview(Map<String,Long> missing) {}
    public record Result(Map<String,Long> added,long skipped) {}
    private void requireAdmin() {
        if(!account.isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Wymagane uprawnienia administratora.");
    }
    public Preview preview() {
        requireAdmin();
        Map<String,Long> counts=new LinkedHashMap<>();
        for(String type:List.of("MEETING","VISIT","EVENT","TRIP","WEDDING")) counts.put(type,0L);
        jdbc.query("select case when m.id is not null then 'MEETING' when v.id is not null then 'VISIT' when e.id is not null then 'EVENT' when t.id is not null then 'TRIP' else 'WEDDING' end as kind, count(*) as amount "
            +TYPES+" where "+ELIGIBLE+" and "+MISSING+" group by kind",
            Map.of("self",account.principal().humanId()),rs->{counts.put(rs.getString("kind"),rs.getLong("amount"));});
        return new Preview(counts);
    }
    @Transactional
    public Result apply() {
        requireAdmin();
        Map<String,Object> params=new HashMap<>(); params.put("self",account.principal().humanId());
        var ids=jdbc.query("select i.id "+TYPES+" where "+ELIGIBLE+" and "+MISSING+" order by i.id",
            params,(rs,n)->rs.getLong(1));
        var self=humans.findById(account.principal().humanId()).orElseThrow();
        var actor=users.getReferenceById(account.id());
        Map<String,Long> added=new LinkedHashMap<>();
        for(String type:List.of("MEETING","VISIT","EVENT","TRIP","WEDDING")) added.put(type,0L);
        long skipped=0;
        for(Long id:ids) {
            var interaction=interactions.findLockedById(id).orElse(null);
            if(interaction==null) { skipped++; continue; }
            // Locking read sees a concurrent committed insert even under MariaDB REPEATABLE READ.
            var existing=jdbc.query("select id from interaction_human where interaction_id=:interaction and human_id=:self for update",
                Map.of("interaction",id,"self",self.getId()),(rs,n)->rs.getLong(1));
            if(!existing.isEmpty() || interaction.participantLink(self.getId()).isPresent()) { skipped++; continue; }
            if(interaction instanceof Wedding wedding && !wedding.isWasIInvited()) { skipped++; continue; }
            if(interaction.getType()==InteractionType.FLATMATE) continue;
            interaction.addParticipant(self,actor);
            added.merge(interaction.getType().name(),1L,Long::sum);
        }
        interactions.flush();
        return new Result(added,skipped);
    }
}
