package org.example.meeter;

import java.sql.DriverManager;
import java.util.*;
import org.example.meeter.auth.*;
import org.example.meeter.interactions.InteractionType;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonSuggestionsTests {
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate sql;
    private CurrentAccount account;
    private PersonSuggestions suggestions;
    @BeforeEach void setup() throws Exception {
        dataSource=new SingleConnectionDataSource(DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MariaDB","sa",""),true);
        sql=new JdbcTemplate(dataSource);
        sql.execute("create table people(id bigint primary key,name varchar(100),surname varchar(100),clique_id bigint,created_by_id bigint)");
        sql.execute("create table cliques(id bigint primary key,clique_name varchar(32))");
        sql.execute("create table interactions(id bigint primary key,created_by_id bigint)");
        sql.execute("create table interaction_human(interaction_id bigint,human_id bigint)");
        sql.execute("create table quotes(id bigint primary key,author_id bigint,is_public boolean)");
        for(String table:List.of("meetings","visits","events","trips","flatmates","weddings")) sql.execute("create table "+table+"(id bigint primary key)");
        account=mock(CurrentAccount.class);
        when(account.id()).thenReturn(1L);
        when(account.principal()).thenReturn(new CurrentAccount.Principal(1L,"test",99L,"Self",Set.of(Role.ROLE_USER)));
        suggestions=new PersonSuggestions(new NamedParameterJdbcTemplate(dataSource),account);
        sql.update("insert into people values(10,'Anna','Nowak',null,1),(20,'Zofia','Nowak',null,1),(30,'Ex','Nowak',3,2),(99,'Self','Self',null,1)");
        sql.update("insert into cliques values(3,' My_Exes ')");
    }
    @AfterEach void close() { dataSource.destroy(); }

    @Test void eachInteractionTypeUsesOwnHistoryBeforeLimitAndKeepsZeroCountPeople() {
        int index=0;
        for(String table:List.of("meetings","visits","events","trips","flatmates","weddings")) {
            long person=120+index;
            sql.update("insert into people values(?,'Zwycięzca','Zet',null,1)",person);
            sql.update("insert into interactions values(?,1),(?,2)",index+1,index+101);
            sql.update("insert into "+table+" values(?),(?)",index+1,index+101);
            sql.update("insert into interaction_human values(?,?),(?,10),(?,99)",index+1,person,index+101,index+1);
            var type=InteractionType.values()[index];
            assertEquals(List.of(person),suggestions.ids("Zwycięzca",1,type,false,false));
            assertEquals(List.of(10L),suggestions.ids("Anna",1,type,false,false));
            assertTrue(suggestions.ids("",100,type,false,false).contains(20L));
            index++;
        }
        // Unshared history cannot change the order, even for administrators.
        when(account.isAdmin()).thenReturn(true);
        assertEquals(List.of(120L),suggestions.ids("Zwycięzca",1,InteractionType.MEETING,false,false));
        assertEquals(List.of(10L,20L),suggestions.ids("Nowak",100,null,true,false));
        assertTrue(suggestions.ids("Nowak",100,null,false,false).contains(30L));
    }

    @Test void quotesCountOnlyVisibleRecordsAndExcludeHiddenClique() {
        sql.update("insert into quotes values(1,20,true),(2,10,false),(3,10,false),(4,10,false),(5,99,false),(6,99,false),(7,30,true),(8,null,true)");
        assertEquals(List.of(99L,20L,10L),suggestions.ids("",100,null,false,true));
        when(account.isAdmin()).thenReturn(true);
        assertEquals(List.of(10L,99L,20L),suggestions.ids("",100,null,false,true));
        assertEquals(List.of(20L),suggestions.ids("Zofia",1,null,false,true));
    }

    @Test void membershipIncludesOtherCreatorsAndExcludesOwnedButUnsharedInteractions() {
        sql.update("insert into interactions values(1,2),(2,1)");
        sql.update("insert into meetings values(1),(2)");
        sql.update("insert into interaction_human values(1,99),(1,20),(2,10)");
        assertEquals(List.of(20L),suggestions.ids("Nowak",1,InteractionType.MEETING,false,false));
        when(account.isAdmin()).thenReturn(true);
        assertEquals(List.of(20L),suggestions.ids("Nowak",1,InteractionType.MEETING,false,false));
        sql.update("delete from interaction_human where interaction_id=1 and human_id=99");
        assertEquals(List.of(10L),suggestions.ids("Nowak",1,InteractionType.MEETING,false,false));
    }

    @Test void tiesUseSurnameNameAndIdAndLiteralSearchEscapesWildcards() {
        sql.update("insert into people values(11,'Anna','Nowak',null,1),(12,'Sto%','Literal',null,1)");
        assertEquals(List.of(10L,11L,20L),suggestions.ids("Nowak",100,null,true,false));
        assertEquals(List.of(12L),suggestions.ids("%",100,null,true,false));
        assertEquals(List.of(),suggestions.ids("_",100,null,true,false));
    }
}
