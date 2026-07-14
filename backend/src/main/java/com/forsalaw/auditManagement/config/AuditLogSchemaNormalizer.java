package com.forsalaw.auditManagement.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogSchemaNormalizer {

    private final JdbcTemplate jdbcTemplate;

    private static final List<String> TEXT_COLUMNS = List.of(
            "id",
            "actor_user_id",
            "module_name",
            "action",
            "method",
            "endpoint",
            "resource_id",
            "ip_address",
            "user_agent",
            "details"
    );

    @PostConstruct
    public void normalizeAuditLogTextColumns() {
        for (String column : TEXT_COLUMNS) {
            if (!isByteaColumn(column)) {
                continue;
            }
            convertByteaToText(column);
        }
    }

    private boolean isByteaColumn(String column) {
        String sql = """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'audit_log'
                  AND column_name = ?
                """;
        List<String> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), column);
        return !rows.isEmpty() && "bytea".equalsIgnoreCase(rows.get(0));
    }

    private void convertByteaToText(String column) {
        String ddl = """
                DO $$
                BEGIN
                  BEGIN
                    EXECUTE 'ALTER TABLE public.audit_log ALTER COLUMN %1$s TYPE text USING convert_from(%1$s, ''UTF8'')';
                  EXCEPTION WHEN character_not_in_repertoire OR untranslatable_character THEN
                    EXECUTE 'ALTER TABLE public.audit_log ALTER COLUMN %1$s TYPE text USING encode(%1$s, ''escape'')';
                  END;
                END $$;
                """.formatted(column);
        jdbcTemplate.execute(ddl);
        log.warn("Audit log schema fix applied: converted audit_log.{} from bytea to text.", column);
    }
}
