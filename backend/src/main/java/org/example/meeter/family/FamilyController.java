package org.example.meeter.family;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
public class FamilyController {
    private final FamilyService service;
    public FamilyController(FamilyService service) { this.service = service; }

    @GetMapping("/api/people/browse/{id}/family")
    public FamilyService.TreeView tree(@PathVariable @Positive Long id) { return service.tree(id); }

    @PostMapping("/api/family/links") @ResponseStatus(HttpStatus.CREATED)
    public FamilyService.LinkView add(@Valid @RequestBody FamilyService.Input input) {
        return service.add(input.type(), input.personAId(), input.personBId());
    }

    @DeleteMapping("/api/family/links/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable @Positive Long id) { service.remove(id); }
}
