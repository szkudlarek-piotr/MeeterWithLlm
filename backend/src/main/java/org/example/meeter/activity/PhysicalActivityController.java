package org.example.meeter.activity;

import static org.example.meeter.activity.PhysicalActivityModels.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/physical-activities") @Validated
public class PhysicalActivityController {
    private final PhysicalActivityService service;

    public PhysicalActivityController(PhysicalActivityService service) { this.service = service; }

    @GetMapping
    public List<View> list() { return service.list(); }

    @GetMapping("/summary")
    public Summary summary() { return service.summary(); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody Input input) { return service.create(input); }

    @PutMapping("/{id}")
    public View update(@PathVariable @Positive Long id, @Valid @RequestBody Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive Long id) { service.delete(id); }
}
