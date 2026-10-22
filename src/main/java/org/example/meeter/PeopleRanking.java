package org.example.meeter;

import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import org.example.meeter.auth.CurrentAccount;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Daily ranking, computed before pagination. No database-specific stored functions required. */
@Service
@Transactional(readOnly = true)
public class PeopleRanking {
    private final NamedParameterJdbcTemplate jdbc;
    private final HumanRepository humans;
    private final CurrentAccount account;
    public PeopleRanking(NamedParameterJdbcTemplate jdbc, HumanRepository humans, CurrentAccount account) {
        this.jdbc=jdbc; this.humans=humans; this.account=account;
    }
    private static final String SEARCH = """
        lower(concat(concat(h.name, ' '), coalesce(h.surname, ''))) like :pattern escape '!'
        and h.id <> :currentHumanId
        and not exists (select 1 from cliques hidden_clique where hidden_clique.id=h.clique_id
          and lower(trim(hidden_clique.clique_name))='my_exes')
        """;
    static final String SCORES = """
        with contributions as (
          select p.human_id,
            case when v.id is not null then i.start_date when m.id is not null then i.start_date
                 when e.id is not null then coalesce(e.me_leaving_date, i.end_date) else i.end_date end as point_date,
            case when v.id is not null then 6 when m.id is not null then 3 when e.id is not null then 1 else 3 end as weight,
            case when v.id is not null then 720.0 when m.id is not null then 360.0 when e.id is not null then 180.0 else 3650.0 end as half_life,
            case when v.id is not null then 1 else 0 end as visit_count
          from interaction_human p join interactions i on i.id=p.interaction_id
          left join visits v on v.id=i.id left join meetings m on m.id=i.id
          left join events e on e.id=i.id left join trips t on t.id=i.id
          where i.created_by_id=:ownerId and (v.id is not null or m.id is not null or e.id is not null or t.id is not null)
          union all
          select h.id, i.start_date,
            case when upper(trim(coalesce(c.clique_name, '')))='WAGS' then 3 else 15 end,
            1825.0, 0
          from weddings w join interactions i on i.id=w.id
          join people h on h.id=w.groom_id or h.id=w.bride_id
          left join cliques c on c.id=h.clique_id
          where i.created_by_id=:ownerId and w.was_i_invited=true
        ), scores as (
          select human_id,
            sum(case when cast(point_date as date)<=:today
                then round(power(0.5, timestampdiff(day, cast(point_date as date), :today)/half_life), 3)*weight
                else 0 end) as points,
            sum(visit_count) as visit_count
          from contributions group by human_id
        )
        """;
    private record Row(long id, long visits, BigDecimal points) {}
    public ApiModels.PeoplePage browse(String query, int page, int size) {
        return browse(query,page,size,LocalDate.now(ZoneId.of("Europe/Warsaw")));
    }
    ApiModels.PeoplePage browse(String query, int page, int size, LocalDate today) {
        String pattern="%"+query.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        var params=new HashMap<String,Object>();
        params.put("ownerId",account.id()); params.put("today",today);
        params.put("currentHumanId",account.principal().humanId());
        params.put("pattern",pattern); params.put("limit",size); params.put("offset",(long)page*size);
        long total=jdbc.queryForObject("select count(*) from people h where "+SEARCH,params,Long.class);
        var rows=jdbc.query(SCORES+"""
            select h.id, coalesce(s.visit_count,0) as visits, coalesce(s.points,0) as points
            from people h left join scores s on s.human_id=h.id where
            """+SEARCH+" order by points desc, lower(coalesce(h.surname,'')), lower(h.name), h.id limit :limit offset :offset",
            params,(rs,n)->new Row(rs.getLong("id"),rs.getLong("visits"),rs.getBigDecimal("points")));
        var byId=new HashMap<Long,Human>();
        humans.findAllById(rows.stream().map(Row::id).toList()).forEach(h->byId.put(h.getId(),h));
        var publicQuotes=new HashMap<Long,ApiModels.PublicQuoteView>();
        if(!rows.isEmpty()) {
            jdbc.query("""
                select id, author_id, quote_text from (
                  select id, author_id, quote_text,
                    row_number() over (partition by author_id order by rand()) as position_in_author
                  from quotes where is_public=true and author_id in (:ids)
                ) candidates where position_in_author=1
                """,Map.of("ids",rows.stream().map(Row::id).toList()),rs->{
                    publicQuotes.put(rs.getLong("author_id"),new ApiModels.PublicQuoteView(rs.getLong("id"),rs.getString("quote_text")));
                });
        }
        var content=rows.stream().map(row->new ApiModels.PersonBrowseView(ApiModels.HumanView.forBrowse(byId.get(row.id())),row.visits(),publicQuotes.get(row.id()))).toList();
        return new ApiModels.PeoplePage(content,page,size,total,(long)page*size+size<total);
    }
}
