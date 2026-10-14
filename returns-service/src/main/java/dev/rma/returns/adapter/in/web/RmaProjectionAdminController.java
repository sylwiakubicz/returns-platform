package dev.rma.returns.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.rma.returns.application.port.in.RebuildRmaProjectionUseCase;

@RestController
@RequestMapping("/admin/projections")
public class RmaProjectionAdminController {
    private final RebuildRmaProjectionUseCase rebuildRmaProjection;

    public RmaProjectionAdminController(RebuildRmaProjectionUseCase rebuildRmaProjection) {
        this.rebuildRmaProjection = rebuildRmaProjection;
    }

    @PostMapping("/rebuild")
    public ResponseEntity<Void> rebuild() {
        rebuildRmaProjection.rebuild();
        return ResponseEntity.noContent().build();
    }
}
