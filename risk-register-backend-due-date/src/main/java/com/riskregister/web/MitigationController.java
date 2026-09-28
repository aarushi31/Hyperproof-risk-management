package com.riskregister.web;

import com.riskregister.service.MitigationService;
import com.riskregister.web.dto.MitigationRequest;
import com.riskregister.web.dto.MitigationResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/risks/{riskId}/mitigations")
public class MitigationController {

    private final MitigationService mitigationService;

    public MitigationController(MitigationService mitigationService) {
        this.mitigationService = mitigationService;
    }

    @GetMapping
    public List<MitigationResponse> list(@PathVariable Long riskId) {
        return mitigationService.list(riskId);
    }

    @PostMapping
    public ResponseEntity<MitigationResponse> create(@PathVariable Long riskId,
                                                     @Valid @RequestBody MitigationRequest request) {
        MitigationResponse created = mitigationService.create(riskId, request);
        return ResponseEntity.created(URI.create("/api/risks/" + riskId + "/mitigations/" + created.id())).body(created);
    }

    @PutMapping("/{mitigationId}")
    public MitigationResponse update(@PathVariable Long riskId, @PathVariable Long mitigationId,
                                     @Valid @RequestBody MitigationRequest request) {
        return mitigationService.update(riskId, mitigationId, request);
    }

    @DeleteMapping("/{mitigationId}")
    public ResponseEntity<Void> delete(@PathVariable Long riskId, @PathVariable Long mitigationId) {
        mitigationService.delete(riskId, mitigationId);
        return ResponseEntity.noContent().build();
    }
}
