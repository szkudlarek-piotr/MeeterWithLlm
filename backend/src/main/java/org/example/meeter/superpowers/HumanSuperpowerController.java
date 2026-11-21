package org.example.meeter.superpowers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/people") @Validated
public class HumanSuperpowerController {
    private final HumanSuperpowerService service;

    public HumanSuperpowerController(HumanSuperpowerService service) {
        this.service = service;
    }

    public record Input(@NotNull @Positive Long superpowerId) {}

    @GetMapping("/{humanId}/superpowers")
    public List<HumanSuperpowerService.View> list(@PathVariable @Positive Long humanId) {
        return service.list(humanId);
    }

    @PostMapping("/{humanId}/superpowers")
    @ResponseStatus(HttpStatus.CREATED)
    public HumanSuperpowerService.View add(@PathVariable @Positive Long humanId, @Valid @RequestBody Input input) {
        return service.add(humanId, input.superpowerId());
    }

    @DeleteMapping("/{humanId}/superpowers/{superpowerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable @Positive Long humanId, @PathVariable @Positive Long superpowerId) {
        service.remove(humanId, superpowerId);
    }
}
