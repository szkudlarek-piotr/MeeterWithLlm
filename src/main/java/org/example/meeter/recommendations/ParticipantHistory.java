package org.example.meeter.recommendations;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runtime features stay account-scoped and are never stored inside shared weights. */
@Service
@Transactional(readOnly = true)
public class ParticipantHistory {
    private final NamedParameterJdbcTemplate jdbc;
    public ParticipantHistory(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> rows(Long owner, Long self) {
        var parameters = Map.of("owner", owner, "self", self);
        var rows = new LinkedHashMap<Long,Map<String,Object>>();
        jdbc.query("""
            select i.id,i.name,i.start_date,coalesce(m.place_string,e.place_string,'') as place_text
            from interactions i left join meetings m on m.id=i.id left join events e on e.id=i.id
            where i.created_by_id=:owner order by i.id
            """, parameters, rs -> {
                while (rs.next()) {
                    var row = new HashMap<String,Object>();
                    row.put("name", rs.getString("name")); row.put("place_text", rs.getString("place_text"));
                    row.put("start_date", rs.getTimestamp("start_date").toLocalDateTime().toLocalDate().toString());
                    row.put("participants", new ArrayList<Long>()); row.put("places", new ArrayList<Map<String,Object>>());
                    rows.put(rs.getLong("id"), row);
                }
                return null;
            });
        jdbc.query("""
            select ih.interaction_id,ih.human_id from interaction_human ih
            join interactions i on i.id=ih.interaction_id
            where i.created_by_id=:owner and ih.human_id<>:self order by ih.interaction_id,ih.human_id
            """, parameters, rs -> {
                while (rs.next()) participants(rows.get(rs.getLong("interaction_id"))).add(rs.getLong("human_id"));
                return null;
            });
        jdbc.query("""
            select distinct x.interaction_id,p.id,p.name from (
                select id as interaction_id,place_id from interactions
                union select trip_id,place_id from trip_place
                union select id,ceremony_place_id from weddings
                union select id,party_place_id from weddings
                union select id,hotel_id from weddings
            ) x join places p on p.id=x.place_id join interactions i on i.id=x.interaction_id
            where i.created_by_id=:owner order by x.interaction_id,p.id
            """, parameters, rs -> {
                while (rs.next()) places(rows.get(rs.getLong("interaction_id"))).add(Map.of("id", rs.getLong("id"), "name", rs.getString("name")));
                return null;
            });
        return List.copyOf(rows.values());
    }

    @SuppressWarnings("unchecked") private List<Long> participants(Map<String,Object> row) {
        return (List<Long>) row.get("participants");
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> places(Map<String,Object> row) {
        return (List<Map<String,Object>>) row.get("places");
    }
}
