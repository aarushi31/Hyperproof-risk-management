package com.riskregister.web;

import com.riskregister.domain.RiskCategory;
import com.riskregister.domain.RiskStatus;
import com.riskregister.service.*;
import com.riskregister.web.dto.RiskRequest;
import com.riskregister.web.dto.RiskResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/risks")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @PostMapping
    public ResponseEntity<RiskResponse> create(@Valid @RequestBody RiskRequest request) {
        RiskResponse created = riskService.create(request);
        return ResponseEntity.created(URI.create("/api/risks/" + created.id())).body(created);
    }

    /**
     * GET /api/risks?category=SECURITY&status=OPEN&sortBy=residual&direction=desc
     * Defaults: sortBy=residual, direction=desc (most severe first). Enum values are case-insensitive.
     */
    @GetMapping
    public List<RiskResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {
        SortField field = QueryParams.parseEnum(SortField.class, sortBy, "sortBy");
        SortDirection dir = QueryParams.parseEnum(SortDirection.class, direction, "direction");
        return riskService.list(new RiskQuery(
                QueryParams.parseEnum(RiskCategory.class, category, "category"),
                QueryParams.parseEnum(RiskStatus.class, status, "status"),
                field != null ? field : SortField.RESIDUAL,
                dir != null ? dir : SortDirection.DESC));
    }

    @GetMapping("/{id}")
    public RiskResponse get(@PathVariable Long id) {
        return riskService.get(id);
    }

    @PutMapping("/{id}")
    public RiskResponse update(@PathVariable Long id, @Valid @RequestBody RiskRequest request) {
        return riskService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        riskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
