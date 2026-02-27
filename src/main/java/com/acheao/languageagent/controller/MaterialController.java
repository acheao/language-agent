package com.acheao.languageagent.controller;

import com.acheao.languageagent.dto.MaterialImportRequest;
import com.acheao.languageagent.entity.Material;
import com.acheao.languageagent.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/materials")
@Tag(name = "Materials", description = "Endpoints for managing learning materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @PostMapping("/import")
    @Operation(summary = "Import new materials", description = "Provide lines of text to be saved as individual materials")
    public ResponseEntity<MaterialService.ImportResult> importMaterials(@RequestBody MaterialImportRequest request) {
        return ResponseEntity.ok(materialService.importMaterials(request));
    }

    @GetMapping
    @Operation(summary = "Get all materials")
    public ResponseEntity<List<Material>> getAllMaterials() {
        return ResponseEntity.ok(materialService.getAllMaterials());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Enable or disable a material")
    public ResponseEntity<Material> updateMaterialStatus(@PathVariable UUID id,
            @RequestBody Map<String, Boolean> update) {
        boolean enabled = update.getOrDefault("enabled", true);
        return ResponseEntity.ok(materialService.updateMaterialEnabled(id, enabled));
    }
}
