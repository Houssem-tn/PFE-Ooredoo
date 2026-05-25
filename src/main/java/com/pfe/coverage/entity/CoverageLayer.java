package com.pfe.coverage.entity;

import com.pfe.coverage.enums.CoverageLayerStatus;
import com.pfe.coverage.enums.CoverageLayerType;
import com.pfe.coverage.enums.CoverageTechnology;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "coverage_layers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoverageLayer {

    @Id
    private String id;

    private String name;
    private CoverageTechnology technology;  // FOUR_G or FIVE_G
    private CoverageLayerType layerType;    // COVERAGE or WS
    private String governorate;             // optional — for territorial scoping
    private String sourceFileName;
    private String color;                   // optional hex color for map rendering

    // Stored as serialized GeoJSON FeatureCollection string
    // TODO: future optimization — store as Object for spatial indexing or use vector tiles
    private String geoJson;

    private String uploadedByUserId;
    private String uploadedByEmail;
    private String uploadedByRole;

    @Builder.Default
    private CoverageLayerStatus status = CoverageLayerStatus.ACTIVE;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
