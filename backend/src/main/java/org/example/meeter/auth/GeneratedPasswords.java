package org.example.meeter.auth;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

/** Explicitly requested local credential export; never returns passwords through HTTP. */
@Service
public class GeneratedPasswords {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final ReentrantLock WRITER=new ReentrantLock();
    private static final String HEADER="username,password,human_id,created_at";
    private final Path file;
    public GeneratedPasswords(@Value("${app.admin.passwords-file:./data/passwords.csv}") String file) {
        this.file=Path.of(file).toAbsolutePath().normalize();
    }
    public String generate() {
        byte[] bytes=new byte[24]; RANDOM.nextBytes(bytes);
        return "M!"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public void append(String username,String password,Long humanId) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Credential export requires a transaction");
        try {
            if(!WRITER.tryLock(20,TimeUnit.SECONDS)) throw unavailable();
        } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw unavailable(); }
        FileChannel channel=null;
        FileLock lock=null;
        boolean registered=false;
        try {
            Files.createDirectories(file.getParent());
            channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            lock=channel.tryLock();
            if(lock==null) throw new IOException();
            long previous=channel.size();
            if(previous>0) {
                var header=ByteBuffer.allocate(HEADER.length()+2);
                while(header.hasRemaining() && channel.read(header)>0) { }
                header.flip();
                String actual=StandardCharsets.UTF_8.decode(header).toString();
                if(!actual.equals(HEADER) && !actual.startsWith(HEADER+"\n") && !actual.startsWith(HEADER+"\r\n")) throw new IOException();
            }
            final FileChannel target=channel;
            final FileLock exclusive=lock;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    try {
                        if(status!=STATUS_COMMITTED) { target.truncate(previous); target.force(true); }
                    } catch(IOException failure) { org.slf4j.LoggerFactory.getLogger(GeneratedPasswords.class).error("Credential CSV rollback failed; inspect the local export."); }
                    finally {
                        close(exclusive,target);
                        WRITER.unlock();
                    }
                }
            });
            registered=true;
            channel.position(previous);
            if(previous==0) write(channel,HEADER+"\r\n");
            else {
                var last=ByteBuffer.allocate(1); channel.read(last,previous-1);
                if(last.array()[0]!='\n') write(channel,"\r\n");
            }
            write(channel,csv(username)+","+csv(password)+","+csv(humanId.toString())+","+csv(Instant.now().toString())+"\r\n");
            channel.force(true);
        } catch(IOException | RuntimeException failure) {
            if(!registered) { close(lock,channel); WRITER.unlock(); }
            throw unavailable();
        }
    }
    private static String csv(String value) { return "\""+value.replace("\"","\"\"")+"\""; }
    private static void write(FileChannel channel,String text) throws IOException {
        var bytes=ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while(bytes.hasRemaining()) channel.write(bytes);
    }
    private static void close(FileLock lock,FileChannel channel) {
        try { if(lock!=null && lock.isValid()) lock.release(); } catch(IOException ignored) { }
        try { if(channel!=null) channel.close(); } catch(IOException ignored) { }
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Nie udało się zapisać pliku passwords.csv. Konto nie zostało utworzone. Sprawdź dostęp do pliku i spróbuj ponownie.");
    }
}
