package com.pfe.stock_management.util;

import com.pfe.stock_management.dto.MaterialQuantityBreakdownDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public final class MaterialQuantityGrouping {

    private MaterialQuantityGrouping() {
    }

    public static <T> List<MaterialQuantityBreakdownDto> group(
            Iterable<T> items,
            Function<T, String> equipmentIdExtractor,
            Function<T, String> equipmentNameExtractor,
            Function<T, String> unitExtractor,
            ToLongFunction<T> quantityExtractor
    ) {
        Map<String, MaterialAccumulator> grouped = new LinkedHashMap<>();
        for (T item : items) {
            if (item == null) continue;
            add(grouped,
                    equipmentIdExtractor.apply(item),
                    equipmentNameExtractor.apply(item),
                    unitExtractor.apply(item),
                    quantityExtractor.applyAsLong(item));
        }
        return toBreakdownList(grouped);
    }

    public static void add(
            Map<String, MaterialAccumulator> grouped,
            String equipmentId,
            String equipmentName,
            String unit,
            long quantity
    ) {
        if (grouped == null || quantity == 0) return;

        String normalizedUnit = normalizeUnit(unit);
        String cleanEquipmentId = clean(equipmentId);
        String cleanEquipmentName = clean(equipmentName);
        String key = (cleanEquipmentId != null ? cleanEquipmentId : cleanEquipmentName != null ? cleanEquipmentName : "material")
                + "::" + normalizedUnit;

        MaterialAccumulator current = grouped.get(key);
        if (current == null) {
            grouped.put(key, new MaterialAccumulator(cleanEquipmentId, cleanEquipmentName, normalizedUnit, quantity));
            return;
        }
        current.quantity += quantity;
    }

    public static List<MaterialQuantityBreakdownDto> toBreakdownList(Map<String, MaterialAccumulator> grouped) {
        List<MaterialQuantityBreakdownDto> rows = new ArrayList<>();
        if (grouped == null) return rows;
        for (MaterialAccumulator value : grouped.values()) {
            rows.add(new MaterialQuantityBreakdownDto(
                    value.equipmentId,
                    value.equipmentName,
                    value.unit,
                    value.quantity));
        }
        return rows;
    }

    public static long totalQuantity(List<MaterialQuantityBreakdownDto> rows) {
        if (rows == null) return 0;
        long total = 0;
        for (MaterialQuantityBreakdownDto row : rows) {
            total += row != null ? row.quantity() : 0;
        }
        return total;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeUnit(String unit) {
        String normalized = clean(unit);
        if (normalized == null) {
            return "UNITS";
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.equals("M") || upper.equals("METER") || upper.equals("METERS")) {
            return "METERS";
        }
        if (upper.equals("PC") || upper.equals("PCS") || upper.equals("PIECE") || upper.equals("PIECES")) {
            return "PIECES";
        }
        return upper;
    }

    public static final class MaterialAccumulator {
        private final String equipmentId;
        private final String equipmentName;
        private final String unit;
        private long quantity;

        private MaterialAccumulator(String equipmentId, String equipmentName, String unit, long quantity) {
            this.equipmentId = equipmentId;
            this.equipmentName = equipmentName;
            this.unit = unit;
            this.quantity = quantity;
        }
    }
}
