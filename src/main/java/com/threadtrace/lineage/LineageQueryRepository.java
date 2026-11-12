package com.threadtrace.lineage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class LineageQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public LineageQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TraceStepRow> traceBackward(long targetLotId, int maxDepth) {
        String sql = """
                WITH RECURSIVE backward_trace (
                    depth, parent_lot_id, child_lot_id, event_id,
                    allocated_quantity, unit, allocated_mass_kg, allocated_pieces, path_str
                ) AS (
                    SELECT
                        1,
                        e.parent_lot_id,
                        e.child_lot_id,
                        e.event_id,
                        e.allocated_quantity,
                        e.unit,
                        e.allocated_mass_kg,
                        e.allocated_pieces,
                        CAST(p.lot_code || ' -> ' || c.lot_code AS VARCHAR(4000))
                    FROM lot_edges e
                    JOIN lots p ON p.id = e.parent_lot_id
                    JOIN lots c ON c.id = e.child_lot_id
                    WHERE e.child_lot_id = ?
                    UNION ALL
                    SELECT
                        bt.depth + 1,
                        e.parent_lot_id,
                        e.child_lot_id,
                        e.event_id,
                        e.allocated_quantity,
                        e.unit,
                        e.allocated_mass_kg,
                        e.allocated_pieces,
                        CAST(p.lot_code || ' -> ' || bt.path_str AS VARCHAR(4000))
                    FROM lot_edges e
                    JOIN backward_trace bt ON e.child_lot_id = bt.parent_lot_id
                    JOIN lots p ON p.id = e.parent_lot_id
                    WHERE bt.depth < ?
                )
                SELECT
                    bt.depth,
                    bt.parent_lot_id,
                    pl.lot_code AS parent_lot_code,
                    pl.lot_type AS parent_lot_type,
                    pl.material_type AS parent_material_type,
                    pl.status AS parent_status,
                    pl.brand_code AS parent_brand_code,
                    pl.supplier_id AS parent_supplier_id,
                    sp.code AS parent_supplier_code,
                    sp.name AS parent_supplier_name,
                    sp.country AS parent_supplier_country,
                    sp.region AS parent_supplier_region,
                    bt.child_lot_id,
                    cl.lot_code AS child_lot_code,
                    cl.lot_type AS child_lot_type,
                    bt.event_id,
                    ev.event_code,
                    ev.event_type,
                    ev.process_step,
                    bt.allocated_quantity,
                    bt.unit,
                    bt.allocated_mass_kg,
                    bt.allocated_pieces,
                    bt.path_str
                FROM backward_trace bt
                JOIN lots pl ON pl.id = bt.parent_lot_id
                JOIN lots cl ON cl.id = bt.child_lot_id
                JOIN lot_events ev ON ev.id = bt.event_id
                LEFT JOIN parties sp ON sp.id = pl.supplier_id
                ORDER BY bt.depth ASC, bt.event_id ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TraceStepRow(
                rs.getInt("depth"),
                rs.getLong("parent_lot_id"),
                rs.getString("parent_lot_code"),
                rs.getString("parent_lot_type"),
                rs.getString("parent_material_type"),
                rs.getString("parent_status"),
                rs.getString("parent_brand_code"),
                rs.getObject("parent_supplier_id", Long.class),
                rs.getString("parent_supplier_code"),
                rs.getString("parent_supplier_name"),
                rs.getString("parent_supplier_country"),
                rs.getString("parent_supplier_region"),
                rs.getLong("child_lot_id"),
                rs.getString("child_lot_code"),
                rs.getString("child_lot_type"),
                rs.getLong("event_id"),
                rs.getString("event_code"),
                rs.getString("event_type"),
                rs.getString("process_step"),
                rs.getBigDecimal("allocated_quantity"),
                rs.getString("unit"),
                rs.getBigDecimal("allocated_mass_kg"),
                rs.getInt("allocated_pieces"),
                rs.getString("path_str")
        ), targetLotId, maxDepth);
    }

    public List<TraceStepRow> traceForward(long sourceLotId, int maxDepth) {
        String sql = """
                WITH RECURSIVE forward_trace (
                    depth, parent_lot_id, child_lot_id, event_id,
                    allocated_quantity, unit, allocated_mass_kg, allocated_pieces, path_str
                ) AS (
                    SELECT
                        1,
                        e.parent_lot_id,
                        e.child_lot_id,
                        e.event_id,
                        e.allocated_quantity,
                        e.unit,
                        e.allocated_mass_kg,
                        e.allocated_pieces,
                        CAST(p.lot_code || ' -> ' || c.lot_code AS VARCHAR(4000))
                    FROM lot_edges e
                    JOIN lots p ON p.id = e.parent_lot_id
                    JOIN lots c ON c.id = e.child_lot_id
                    WHERE e.parent_lot_id = ?
                    UNION ALL
                    SELECT
                        ft.depth + 1,
                        e.parent_lot_id,
                        e.child_lot_id,
                        e.event_id,
                        e.allocated_quantity,
                        e.unit,
                        e.allocated_mass_kg,
                        e.allocated_pieces,
                        CAST(ft.path_str || ' -> ' || c.lot_code AS VARCHAR(4000))
                    FROM lot_edges e
                    JOIN forward_trace ft ON e.parent_lot_id = ft.child_lot_id
                    JOIN lots c ON c.id = e.child_lot_id
                    WHERE ft.depth < ?
                )
                SELECT
                    ft.depth,
                    ft.parent_lot_id,
                    pl.lot_code AS parent_lot_code,
                    pl.lot_type AS parent_lot_type,
                    pl.material_type AS parent_material_type,
                    pl.status AS parent_status,
                    pl.brand_code AS parent_brand_code,
                    pl.supplier_id AS parent_supplier_id,
                    sp.code AS parent_supplier_code,
                    sp.name AS parent_supplier_name,
                    sp.country AS parent_supplier_country,
                    sp.region AS parent_supplier_region,
                    ft.child_lot_id,
                    cl.lot_code AS child_lot_code,
                    cl.lot_type AS child_lot_type,
                    ft.event_id,
                    ev.event_code,
                    ev.event_type,
                    ev.process_step,
                    ft.allocated_quantity,
                    ft.unit,
                    ft.allocated_mass_kg,
                    ft.allocated_pieces,
                    ft.path_str
                FROM forward_trace ft
                JOIN lots pl ON pl.id = ft.parent_lot_id
                JOIN lots cl ON cl.id = ft.child_lot_id
                JOIN lot_events ev ON ev.id = ft.event_id
                LEFT JOIN parties sp ON sp.id = pl.supplier_id
                ORDER BY ft.depth ASC, ft.event_id ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TraceStepRow(
                rs.getInt("depth"),
                rs.getLong("parent_lot_id"),
                rs.getString("parent_lot_code"),
                rs.getString("parent_lot_type"),
                rs.getString("parent_material_type"),
                rs.getString("parent_status"),
                rs.getString("parent_brand_code"),
                rs.getObject("parent_supplier_id", Long.class),
                rs.getString("parent_supplier_code"),
                rs.getString("parent_supplier_name"),
                rs.getString("parent_supplier_country"),
                rs.getString("parent_supplier_region"),
                rs.getLong("child_lot_id"),
                rs.getString("child_lot_code"),
                rs.getString("child_lot_type"),
                rs.getLong("event_id"),
                rs.getString("event_code"),
                rs.getString("event_type"),
                rs.getString("process_step"),
                rs.getBigDecimal("allocated_quantity"),
                rs.getString("unit"),
                rs.getBigDecimal("allocated_mass_kg"),
                rs.getInt("allocated_pieces"),
                rs.getString("path_str")
        ), sourceLotId, maxDepth);
    }

    public record TraceStepRow(
            int depth,
            Long parentLotId,
            String parentLotCode,
            String parentLotType,
            String parentMaterialType,
            String parentStatus,
            String parentBrandCode,
            Long parentSupplierId,
            String parentSupplierCode,
            String parentSupplierName,
            String parentSupplierCountry,
            String parentSupplierRegion,
            Long childLotId,
            String childLotCode,
            String childLotType,
            Long eventId,
            String eventCode,
            String eventType,
            String processStep,
            BigDecimal allocatedQuantity,
            String unit,
            BigDecimal allocatedMassKg,
            int allocatedPieces,
            String path
    ) {
    }
}
