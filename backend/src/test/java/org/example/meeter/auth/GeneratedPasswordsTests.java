package org.example.meeter.auth;

import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedPasswordsTests {
    @TempDir Path directory;
    private SingleConnectionDataSource source;
    private TransactionTemplate transaction;
    @BeforeEach void setup() throws Exception {
        source=new SingleConnectionDataSource(DriverManager.getConnection("jdbc:h2:mem:password-export-"+UUID.randomUUID(),"sa",""),true);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    @AfterEach void close() { source.destroy(); }
    @Test void generatesDistinctStrongPasswordsAndNeverExportsOutsideTransaction() {
        var passwords=new GeneratedPasswords(directory.resolve("passwords.csv").toString());
        Set<String> draws=new HashSet<>();
        for(int n=0;n<50;n++) {
            String password=passwords.generate();
            assertTrue(password.matches("M![A-Za-z0-9_-]{32}")); draws.add(password);
        }
        assertEquals(50,draws.size());
        assertThrows(IllegalStateException.class,()->passwords.append("alice",passwords.generate(),1L));
        assertFalse(Files.exists(directory.resolve("passwords.csv")));
    }
    @Test void rollbackRestoresPreviousCsvAndReleasesWriter() throws Exception {
        Path file=directory.resolve("passwords.csv");
        var passwords=new GeneratedPasswords(file.toString());
        transaction.executeWithoutResult(status->passwords.append("alice",passwords.generate(),1L));
        byte[] previous=Files.readAllBytes(file);
        transaction.executeWithoutResult(status->{passwords.append("bob",passwords.generate(),2L); status.setRollbackOnly();});
        assertTrue(Arrays.equals(previous,Files.readAllBytes(file)));
        transaction.executeWithoutResult(status->passwords.append("carol",passwords.generate(),3L));
        var lines=Files.readAllLines(file);
        assertEquals(3,lines.size());
        assertTrue(lines.get(0).equals("username,password,human_id,created_at"));
        assertTrue(lines.get(1).startsWith("\"alice\","));
        assertTrue(lines.get(2).startsWith("\"carol\","));
    }
    @Test void incompatibleExistingFileIsPreserved() throws Exception {
        String unrelated="username,password,human_id,created_at,extra\n";
        Path file=directory.resolve("passwords.csv"); Files.writeString(file,unrelated);
        var passwords=new GeneratedPasswords(file.toString());
        assertThrows(ResponseStatusException.class,()->transaction.executeWithoutResult(status->passwords.append("alice",passwords.generate(),1L)));
        assertTrue(Files.readString(file).equals(unrelated));
    }
}
