package com.pfe.stock_management.util;

import com.pfe.stock_management.dto.MaterialQuantityBreakdownDto;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialQuantityGroupingTest {

    @Test
    void add_shouldAggregateDifferentPieceSpellingsIntoOnePiecesBucket() {
        Map<String, MaterialQuantityGrouping.MaterialAccumulator> grouped = new LinkedHashMap<>();

        MaterialQuantityGrouping.add(grouped, "eq-1", "ONT Box", "PCS", 2);
        MaterialQuantityGrouping.add(grouped, "eq-1", "ONT Box", "Piece", 3);
        MaterialQuantityGrouping.add(grouped, "eq-1", "ONT Box", "pc", 1);

        List<MaterialQuantityBreakdownDto> rows = MaterialQuantityGrouping.toBreakdownList(grouped);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).unit()).isEqualTo("PIECES");
        assertThat(rows.get(0).quantity()).isEqualTo(6);
    }

    @Test
    void add_shouldAggregateDifferentMeterSpellingsIntoOneMetersBucket() {
        Map<String, MaterialQuantityGrouping.MaterialAccumulator> grouped = new LinkedHashMap<>();

        MaterialQuantityGrouping.add(grouped, "eq-2", "Fiber Cable", "m", 50);
        MaterialQuantityGrouping.add(grouped, "eq-2", "Fiber Cable", "METER", 25);
        MaterialQuantityGrouping.add(grouped, "eq-2", "Fiber Cable", "meters", 10);

        List<MaterialQuantityBreakdownDto> rows = MaterialQuantityGrouping.toBreakdownList(grouped);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).unit()).isEqualTo("METERS");
        assertThat(rows.get(0).quantity()).isEqualTo(85);
    }

    @Test
    void add_shouldNeverMixPiecesAndMetersForTheSameEquipment() {
        Map<String, MaterialQuantityGrouping.MaterialAccumulator> grouped = new LinkedHashMap<>();

        MaterialQuantityGrouping.add(grouped, "eq-3", "Cable Drum", "PCS", 4);
        MaterialQuantityGrouping.add(grouped, "eq-3", "Cable Drum", "METERS", 100);

        List<MaterialQuantityBreakdownDto> rows = MaterialQuantityGrouping.toBreakdownList(grouped);

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.unit()).isEqualTo("PIECES");
            assertThat(r.quantity()).isEqualTo(4);
        });
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.unit()).isEqualTo("METERS");
            assertThat(r.quantity()).isEqualTo(100);
        });
        // The two buckets must never be summed into a single total.
        assertThat(MaterialQuantityGrouping.totalQuantity(rows)).isEqualTo(104);
    }

    @Test
    void add_shouldSkipZeroQuantityEntries() {
        Map<String, MaterialQuantityGrouping.MaterialAccumulator> grouped = new LinkedHashMap<>();

        MaterialQuantityGrouping.add(grouped, "eq-4", "Router", "PCS", 0);

        assertThat(MaterialQuantityGrouping.toBreakdownList(grouped)).isEmpty();
    }

    @Test
    void add_shouldFallBackToUnitsWhenUnitIsBlank() {
        Map<String, MaterialQuantityGrouping.MaterialAccumulator> grouped = new LinkedHashMap<>();

        MaterialQuantityGrouping.add(grouped, "eq-5", "Mystery Item", "  ", 3);

        List<MaterialQuantityBreakdownDto> rows = MaterialQuantityGrouping.toBreakdownList(grouped);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).unit()).isEqualTo("UNITS");
    }

    @Test
    void add_shouldGroupSeparatelyByEquipmentIdEvenWithSameUnit() {
        Map<String, MaterialQuantityGrouping.MaterialAccumulator> grouped = new LinkedHashMap<>();

        MaterialQuantityGrouping.add(grouped, "eq-1", "ONT Box", "PCS", 2);
        MaterialQuantityGrouping.add(grouped, "eq-2", "Router", "PCS", 5);

        List<MaterialQuantityBreakdownDto> rows = MaterialQuantityGrouping.toBreakdownList(grouped);
        assertThat(rows).hasSize(2);
    }
}
