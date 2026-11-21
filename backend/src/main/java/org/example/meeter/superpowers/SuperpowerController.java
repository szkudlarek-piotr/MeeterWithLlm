package org.example.meeter.superpowers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/superpowers") @Validated
public class SuperpowerController {
    private final SuperpowerService service;

    public SuperpowerController(SuperpowerService service) {
        this.service = service;
    }

    public record View(Long id, String superpowerName) {
        static View of(Superpower superpower) {
            return new View(superpower.getId(), superpower.getSuperpowerName());
        }
    }

    public record Input(@NotBlank @Size(max = 255) String superpowerName) {}

    @GetMapping
    public List<View> list() {
        return service.list().stream().map(View::of).toList();
    }

    @GetMapping("/{id}")
    public View get(@PathVariable @Positive Long id) {
        return View.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody Input input) {
        return View.of(service.create(input.superpowerName()));
    }
}
