package org.example.meeter.photos;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.example.meeter.*;
import org.example.meeter.auth.CurrentAccount;
import org.example.meeter.multimedia.photo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CliquePhotoImport {
    private static final Pattern FILENAME=Pattern.compile("([1-9][0-9]*)\\.(jpg|jpeg|png|gif)",Pattern.CASE_INSENSITIVE);
    private final CurrentAccount account;
    private final CliqueRepository cliques;
    private final PhotoRepository photos;
    private final CliquePhotoStorage storage;
    private final TransactionTemplate transaction;
    public CliquePhotoImport(CurrentAccount account,CliqueRepository cliques,PhotoRepository photos,
            CliquePhotoStorage storage,PlatformTransactionManager manager) {
        this.account=account; this.cliques=cliques; this.photos=photos; this.storage=storage;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public record Item(String filename,Long cliqueId,String status,String message) {}
    public record Report(long imported,long skipped,long failed,List<Item> items) {}
    public Report importFolder(String folder) {
        if(!account.isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Wymagane uprawnienia administratora.");
        final Path root;
        final List<Path> files;
        try {
            root=Path.of(folder).toRealPath();
            if(!Files.isDirectory(root)) throw new java.io.IOException();
            try(var entries=Files.list(root)) { files=entries.sorted().limit(5001).toList(); }
            if(files.size()>5000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Folder może zawierać maksymalnie 5000 plików. Podziel import na mniejsze foldery.");
        } catch(java.io.IOException | InvalidPathException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Folder nie istnieje lub backend nie ma dostępu do jego odczytu.");
        }
        Map<Long,Integer> counts=new HashMap<>();
        for(Path file:files) {
            Long id=cliqueId(file);
            if(id!=null) counts.merge(id,1,Integer::sum);
        }
        var items=new ArrayList<Item>();
        for(Path file:files) {
            String name=file.getFileName().toString();
            Long id=cliqueId(file);
            if(id==null) { items.add(new Item(name,null,"SKIPPED","Oczekiwana nazwa: ID.jpg, ID.png lub ID.gif.")); continue; }
            if(counts.get(id)>1) { items.add(new Item(name,id,"FAILED","Kilka plików dla tej samej kliki. Pozostaw jeden.")); continue; }
            try {
                Item item=transaction.execute(status -> importFile(root,file,id));
                items.add(item);
            } catch(Exception failure) {
                // Never return filesystem paths, database messages or exception details.
                items.add(new Item(name,id,"FAILED","Nie udało się zapisać zdjęcia. Użyj JPG, PNG lub GIF do 4 MB i 16 megapikseli."));
            }
        }
        return new Report(items.stream().filter(i->i.status().equals("IMPORTED")).count(),
            items.stream().filter(i->i.status().equals("SKIPPED")).count(),
            items.stream().filter(i->i.status().equals("FAILED")).count(),items);
    }
    private Item importFile(Path root,Path file,Long id) {
        String name=file.getFileName().toString();
        var clique=cliques.lockForPhoto(id).orElse(null);
        if(clique==null) return new Item(name,id,"SKIPPED","Nie ma kliki o tym ID.");
        if(clique.getPhoto()!=null) return new Item(name,id,"SKIPPED","Klika ma już zdjęcie.");
        try {
            if(Files.isSymbolicLink(file) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)
                    || !file.toRealPath().startsWith(root)) throw new java.io.IOException();
            byte[] bytes;
            try(var input=Files.newInputStream(file)) { bytes=input.readNBytes(RemotePhotoClient.MAX_BYTES+1); }
            String stored=storage.store(bytes);
            var photo=new Photo();
            photo.setPhotoName(stored); photo.setPhotoType(PhotoType.CLIQUE);
            photo.setGenerationTime(java.time.LocalDateTime.now());
            clique.setPhoto(photos.save(photo));
            cliques.flush();
            return new Item(name,id,"IMPORTED","Zaimportowano zdjęcie.");
        } catch(java.io.IOException failure) { throw new IllegalStateException("Source image unavailable"); }
    }
    private static Long cliqueId(Path file) {
        var matcher=FILENAME.matcher(file.getFileName().toString());
        if(!matcher.matches()) return null;
        try { return Long.valueOf(matcher.group(1)); } catch(NumberFormatException ignored) { return null; }
    }
}
