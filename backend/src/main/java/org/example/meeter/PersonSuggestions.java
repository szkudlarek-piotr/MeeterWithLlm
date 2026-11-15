package org.example.meeter;

import java.util.*;
import org.example.meeter.auth.CurrentAccount;
import org.example.meeter.interactions.InteractionType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Context ordering is computed before LIMIT; counts never leave this service. */
@Service @Transactional(readOnly=true)
public class PersonSuggestions {
    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentAccount account;
    public PersonSuggestions(NamedParameterJdbcTemplate jdbc,CurrentAccount account) {
        this.jdbc=jdbc; this.account=account;
    }
    public List<Long> ids(String query,int limit,InteractionType type,boolean ownedOnly,boolean quotes) {
        var params=new HashMap<String,Object>();
        params.put("owner",account.id()); params.put("self",account.principal().humanId());
        params.put("admin",account.isAdmin()); params.put("limit",limit);
        params.put("pattern","%"+query.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        String counts="";
        String order="";
        if(quotes) {
            counts="""
                left join (select author_id as human_id, count(*) as amount from quotes
                  where is_public=true or author_id=:self or :admin=true
                  group by author_id) frequency on frequency.human_id=h.id
                """;
            order="coalesce(frequency.amount,0) desc, ";
        } else if(type!=null) {
            // Table names come exclusively from this exhaustive enum mapping.
            String table=switch(type) {
                case MEETING -> "meetings"; case VISIT -> "visits"; case EVENT -> "events";
                case TRIP -> "trips"; case FLATMATE -> "flatmates"; case WEDDING -> "weddings";
            };
            counts="left join (select p.human_id, count(distinct i.id) as amount from interaction_human p "
                +"join interactions i on i.id=p.interaction_id join "+table+" typed on typed.id=i.id "
                +"where exists (select 1 from interaction_human self_link where self_link.interaction_id=i.id and self_link.human_id=:self) "
                +"group by p.human_id) frequency on frequency.human_id=h.id ";
            order="coalesce(frequency.amount,0) desc, ";
        }
        String filter=ownedOnly?" and h.created_by_id=:owner":"";
        if(quotes) filter+=" and not exists (select 1 from cliques c where c.id=h.clique_id and lower(trim(c.clique_name))='my_exes')";
        return jdbc.query("select h.id from people h "+counts
            +" where lower(concat(concat(h.name,' '),coalesce(h.surname,''))) like :pattern escape '!'"+filter
            +" order by "+order+"lower(coalesce(h.surname,'')), lower(h.name), h.id limit :limit",
            params,(rs,n)->rs.getLong("id"));
    }
}
