package com.ruoyi.web.service.quant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;

@Service
public class QuantRoadSymbolScopeService
{
    private static final List<Map<String, Object>> DEFAULT_INDEX_ETF_MAPPINGS = List.of(
            mappingSeed("000300", "沪深300", "510300", "沪深300ETF", List.of("159919", "510310"), List.of("沪深300ETF备选A", "沪深300ETF备选B")),
            mappingSeed("000905", "中证500", "510500", "中证500ETF", List.of("159922", "510510"), List.of("中证500ETF备选A", "中证500ETF备选B")),
            mappingSeed("000852", "中证1000", "512100", "中证1000ETF", List.of("159845", "560010"), List.of("中证1000ETF备选A", "中证1000ETF备选B")),
            mappingSeed("399006", "创业板指", "159915", "创业板ETF", List.of("159952", "159949"), List.of("创业板ETF备选A", "创业板ETF备选B")));

    private final JdbcTemplate jdbcTemplate;

    public QuantRoadSymbolScopeService(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void initializeSchema()
    {
        ensureSchema();
    }

    public Map<String, Object> symbolScopeOptions()
    {
        return withSchemaRetry(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("presetScopes", List.of(
                    scopeOption("all_stocks", "全市场", "默认非 ST 个股范围，适合验证广泛适用性。", true, false),
                    scopeOption("stock_pool", "个股池", "规则池 + 人工调仓后的正式个股交易范围。", true, true),
                    scopeOption("etf_pool", "ETF池", "把 ETF 作为独立交易对象管理。", true, true),
                    scopeOption("index_mapped_etf_pool", "指数映射ETF池", "按指数 -> 主ETF -> 备选ETF 管理默认执行范围。", true, true)));
            result.put("poolOptions", symbolPools());
            result.put("indexEtfMappings", indexEtfMappings());
            result.put("constraintOptions", List.of(
                    constraintOption("whitelist", "白名单", "在预设范围上强制保留的标的"),
                    constraintOption("blacklist", "黑名单", "在预设范围上强制排除的标的"),
                    constraintOption("adHocSymbols", "临时代码清单", "本次研究/回测临时补充的标的")));
            return result;
        });
    }

    public String resolveScopeType(String scopeType)
    {
        return normalizeScopeType(scopeType);
    }

    public List<Map<String, Object>> symbolPools()
    {
        return withSchemaRetry(() -> {
            List<Map<String, Object>> pools = jdbcTemplate.queryForList(
                    "SELECT pool_code, pool_name, pool_type, scope_type, description, rule_definition, status, version_no, sort_order, update_time " +
                            "FROM quant_symbol_pool ORDER BY sort_order ASC, pool_code ASC");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> pool : pools)
            {
                String poolCode = String.valueOf(pool.get("pool_code"));
                List<Map<String, Object>> counts = jdbcTemplate.queryForList(
                        "SELECT inclusion_status, COUNT(1) AS cnt FROM quant_symbol_pool_member WHERE pool_code = ? GROUP BY inclusion_status",
                        poolCode);
                int includedCount = 0;
                int excludedCount = 0;
                int candidateCount = 0;
                for (Map<String, Object> count : counts)
                {
                    String status = String.valueOf(count.get("inclusion_status"));
                    int current = toInt(count.get("cnt"));
                    if ("INCLUDED".equalsIgnoreCase(status))
                    {
                        includedCount = current;
                    }
                    else if ("EXCLUDED".equalsIgnoreCase(status))
                    {
                        excludedCount = current;
                    }
                    else if ("CANDIDATE".equalsIgnoreCase(status))
                    {
                        candidateCount = current;
                    }
                }
                List<String> sampleSymbols = jdbcTemplate.query(
                        "SELECT stock_code FROM quant_symbol_pool_member " +
                                "WHERE pool_code = ? AND inclusion_status = 'INCLUDED' ORDER BY sort_order ASC, stock_code ASC LIMIT 5",
                        (rs, rowNum) -> rs.getString(1),
                        poolCode);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("poolCode", poolCode);
                row.put("poolName", pool.get("pool_name"));
                row.put("poolType", pool.get("pool_type"));
                row.put("scopeType", pool.get("scope_type"));
                row.put("description", pool.get("description"));
                row.put("ruleDefinition", parseJsonObject(pool.get("rule_definition")));
                row.put("includedCount", includedCount);
                row.put("excludedCount", excludedCount);
                row.put("candidateCount", candidateCount);
                row.put("sampleSymbols", sampleSymbols);
                row.put("status", toInt(pool.get("status")) == 1 ? "ACTIVE" : "INACTIVE");
                row.put("versionNo", toInt(pool.get("version_no")));
                row.put("updateTime", pool.get("update_time"));
                result.add(row);
            }
            return result;
        });
    }

    public Map<String, Object> symbolPoolDetail(String poolCode)
    {
        String resolvedPoolCode = normalizePoolCode(poolCode);
        if (resolvedPoolCode == null)
        {
            return new LinkedHashMap<>();
        }
        return withSchemaRetry(() -> {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT pool_code, pool_name, pool_type, scope_type, description, rule_definition, status, version_no, sort_order, update_time " +
                            "FROM quant_symbol_pool WHERE pool_code = ? LIMIT 1",
                    resolvedPoolCode);
            if (rows.isEmpty())
            {
                return new LinkedHashMap<>();
            }
            Map<String, Object> pool = rows.get(0);
            List<Map<String, Object>> members = jdbcTemplate.queryForList(
                    "SELECT stock_code, stock_name, asset_type, source_type, inclusion_status, mapped_index_code, mapped_role, note, sort_order, update_time " +
                            "FROM quant_symbol_pool_member WHERE pool_code = ? ORDER BY sort_order ASC, stock_code ASC",
                    resolvedPoolCode);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("poolCode", resolvedPoolCode);
            result.put("poolName", pool.get("pool_name"));
            result.put("poolType", pool.get("pool_type"));
            result.put("scopeType", pool.get("scope_type"));
            result.put("description", pool.get("description"));
            result.put("ruleDefinition", parseJsonObject(pool.get("rule_definition")));
            result.put("status", toInt(pool.get("status")) == 1 ? "ACTIVE" : "INACTIVE");
            result.put("versionNo", toInt(pool.get("version_no")));
            result.put("updateTime", pool.get("update_time"));
            result.put("members", members.stream().map(this::toCamelCaseMap).toList());
            result.put("summary", buildPoolMemberSummary(members));
            return result;
        });
    }

    public List<Map<String, Object>> indexEtfMappings()
    {
        return withSchemaRetry(() -> jdbcTemplate.queryForList(
                "SELECT index_code, index_name, primary_etf_code, primary_etf_name, candidate_etf_codes, candidate_etf_names, status, note, update_time " +
                        "FROM quant_index_etf_mapping WHERE status = 1 ORDER BY index_code ASC")
                .stream()
                .map(row -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("indexCode", row.get("index_code"));
                    result.put("indexName", row.get("index_name"));
                    result.put("primaryEtfCode", row.get("primary_etf_code"));
                    result.put("primaryEtfName", row.get("primary_etf_name"));
                    result.put("candidateEtfCodes", parseJsonArray(row.get("candidate_etf_codes")));
                    result.put("candidateEtfNames", parseJsonArray(row.get("candidate_etf_names")));
                    result.put("note", row.get("note"));
                    result.put("updateTime", row.get("update_time"));
                    return result;
                })
                .toList());
    }

    public Map<String, Object> symbolScopePreview(
            String scopeType,
            String scopePoolCode,
            List<String> requestedSymbols,
            List<String> whitelist,
            List<String> blacklist,
            List<String> adHocSymbols)
    {
        return withSchemaRetry(() -> {
            List<String> symbols = resolveScopeSymbols(scopeType, scopePoolCode, requestedSymbols, whitelist, blacklist, adHocSymbols);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scopeType", resolveScopeType(scopeType));
            result.put("scopePoolCode", normalizePoolCode(scopePoolCode));
            result.put("resolvedCount", symbols.size());
            result.put("symbols", symbols.stream().limit(30).toList());
            result.put("hasMore", symbols.size() > 30);
            result.put("whitelistCount", normalizeSymbols(whitelist).size());
            result.put("blacklistCount", normalizeSymbols(blacklist).size());
            result.put("adHocCount", normalizeSymbols(adHocSymbols).size());
            return result;
        });
    }

    public List<String> resolveScopeSymbols(
            String scopeType,
            String scopePoolCode,
            List<String> requestedSymbols,
            List<String> whitelist,
            List<String> blacklist,
            List<String> adHocSymbols)
    {
        return withSchemaRetry(() -> {
            Set<String> resolved = new LinkedHashSet<>();
            List<String> normalizedRequestedSymbols = normalizeSymbols(requestedSymbols);
            if (!normalizedRequestedSymbols.isEmpty())
            {
                resolved.addAll(normalizedRequestedSymbols);
            }
            else
            {
                String normalizedScopeType = resolveScopeType(scopeType);
                if ("stock_pool".equals(normalizedScopeType) || "etf_pool".equals(normalizedScopeType) || "index_mapped_etf_pool".equals(normalizedScopeType))
                {
                    String poolCode = normalizePoolCode(scopePoolCode);
                    if (poolCode == null)
                    {
                        poolCode = defaultPoolCodeForType(normalizedScopeType);
                    }
                    if (poolCode != null)
                    {
                        resolved.addAll(loadPoolIncludedSymbols(poolCode));
                    }
                }
                else
                {
                    resolved.addAll(loadAllStockSymbols());
                }
            }
            resolved.addAll(normalizeSymbols(whitelist));
            resolved.addAll(normalizeSymbols(adHocSymbols));
            for (String item : normalizeSymbols(blacklist))
            {
                resolved.remove(item);
            }
            return resolved.stream().sorted().toList();
        });
    }

    private <T> T withSchemaRetry(java.util.function.Supplier<T> supplier)
    {
        try
        {
            return supplier.get();
        }
        catch (DataAccessException ex)
        {
            if (!looksLikeMissingSchema(ex))
            {
                throw ex;
            }
            ensureSchema();
            return supplier.get();
        }
    }

    private boolean looksLikeMissingSchema(DataAccessException ex)
    {
        String message = ex.getMessage();
        if (message == null)
        {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("quant_symbol_pool")
                || normalized.contains("quant_symbol_pool_member")
                || normalized.contains("quant_index_etf_mapping")
                || normalized.contains("relation")
                || normalized.contains("table");
    }

    private void ensureSchema()
    {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS quant_symbol_pool (" +
                        "pool_code VARCHAR(64) PRIMARY KEY," +
                        "pool_name VARCHAR(128) NOT NULL," +
                        "pool_type VARCHAR(32) NOT NULL," +
                        "scope_type VARCHAR(32) NOT NULL," +
                        "description TEXT," +
                        "rule_definition JSONB," +
                        "status SMALLINT DEFAULT 1," +
                        "version_no INT DEFAULT 1," +
                        "sort_order INT DEFAULT 0," +
                        "create_time TIMESTAMP DEFAULT NOW()," +
                        "update_time TIMESTAMP DEFAULT NOW())");
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS quant_symbol_pool_member (" +
                        "id BIGSERIAL PRIMARY KEY," +
                        "pool_code VARCHAR(64) NOT NULL," +
                        "stock_code VARCHAR(20) NOT NULL," +
                        "stock_name VARCHAR(128)," +
                        "asset_type VARCHAR(20) NOT NULL," +
                        "source_type VARCHAR(32) NOT NULL," +
                        "inclusion_status VARCHAR(16) NOT NULL," +
                        "mapped_index_code VARCHAR(20)," +
                        "mapped_role VARCHAR(16) NOT NULL DEFAULT 'NA'," +
                        "note TEXT," +
                        "sort_order INT DEFAULT 0," +
                        "create_time TIMESTAMP DEFAULT NOW()," +
                        "update_time TIMESTAMP DEFAULT NOW()," +
                        "UNIQUE (pool_code, stock_code, inclusion_status, mapped_role))");
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS quant_index_etf_mapping (" +
                        "id BIGSERIAL PRIMARY KEY," +
                        "index_code VARCHAR(20) NOT NULL UNIQUE," +
                        "index_name VARCHAR(128) NOT NULL," +
                        "primary_etf_code VARCHAR(20) NOT NULL," +
                        "primary_etf_name VARCHAR(128)," +
                        "candidate_etf_codes JSONB," +
                        "candidate_etf_names JSONB," +
                        "status SMALLINT DEFAULT 1," +
                        "note TEXT," +
                        "create_time TIMESTAMP DEFAULT NOW()," +
                        "update_time TIMESTAMP DEFAULT NOW())");
        seedDefaultPools();
        seedIndexEtfMappings();
        seedPoolMembers();
        seedIndexMappedPoolMembers();
    }

    private void seedDefaultPools()
    {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM quant_symbol_pool", Integer.class);
        if (count != null && count > 0)
        {
            return;
        }
        insertPool("STOCK_CORE", "个股规则池", "stock_pool", "stock_pool",
                "默认非 ST 个股规则池，支持白名单/黑名单与人工增删。",
                Map.of("source", "non_st_a_share", "manualOverlay", true, "supportsWhitelist", true, "supportsBlacklist", true), 10);
        insertPool("ETF_CORE", "ETF池", "etf_pool", "etf_pool",
                "ETF 独立交易池，作为指数投资和低波动策略的执行对象。",
                Map.of("source", "etf_universe", "manualOverlay", true, "supportsWhitelist", true, "supportsBlacklist", true), 20);
        insertPool("INDEX_ETF_DEFAULT", "指数映射ETF池", "index_mapped_etf_pool", "index_mapped_etf_pool",
                "按指数 -> 主ETF -> 备选ETF 管理默认执行范围。",
                Map.of("source", "index_primary_etf_mapping", "manualOverlay", true, "defaultRole", "PRIMARY"), 30);
    }

    private void seedIndexEtfMappings()
    {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM quant_index_etf_mapping", Integer.class);
        if (count != null && count > 0)
        {
            return;
        }
        for (Map<String, Object> item : DEFAULT_INDEX_ETF_MAPPINGS)
        {
            jdbcTemplate.update(
                    "INSERT INTO quant_index_etf_mapping (" +
                            "index_code, index_name, primary_etf_code, primary_etf_name, candidate_etf_codes, candidate_etf_names, status, note, create_time, update_time) " +
                            "VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), 1, ?, NOW(), NOW()) " +
                            "ON CONFLICT (index_code) DO NOTHING",
                    item.get("indexCode"),
                    item.get("indexName"),
                    item.get("primaryEtfCode"),
                    item.get("primaryEtfName"),
                    JSON.toJSONString(item.get("candidateEtfCodes")),
                    JSON.toJSONString(item.get("candidateEtfNames")),
                    "seeded-default-mapping");
        }
    }

    private void seedPoolMembers()
    {
        seedPoolMemberByQuery(
                "STOCK_CORE",
                "stock",
                "RULE",
                "INCLUDED",
                "SELECT stock_code, stock_name FROM stock_basic " +
                        "WHERE COALESCE(is_st, 0) = 0 AND NOT (UPPER(COALESCE(industry, '')) = 'ETF' OR UPPER(COALESCE(stock_name, '')) LIKE '%ETF%') " +
                        "ORDER BY stock_code LIMIT 80");
        seedPoolMemberByQuery(
                "ETF_CORE",
                "etf",
                "RULE",
                "INCLUDED",
                "SELECT stock_code, stock_name FROM stock_basic " +
                        "WHERE UPPER(COALESCE(industry, '')) = 'ETF' OR UPPER(COALESCE(stock_name, '')) LIKE '%ETF%' " +
                        "ORDER BY stock_code LIMIT 60");
    }

    private void seedPoolMemberByQuery(String poolCode, String assetType, String sourceType, String inclusionStatus, String sql)
    {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM quant_symbol_pool_member WHERE pool_code = ?",
                Integer.class,
                poolCode);
        if (count != null && count > 0)
        {
            return;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        int sortOrder = 10;
        for (Map<String, Object> row : rows)
        {
            upsertPoolMember(
                    poolCode,
                    String.valueOf(row.get("stock_code")),
                    row.get("stock_name") == null ? null : String.valueOf(row.get("stock_name")),
                    assetType,
                    sourceType,
                    inclusionStatus,
                    null,
                    null,
                    "seeded-default-member",
                    sortOrder);
            sortOrder += 10;
        }
    }

    private void seedIndexMappedPoolMembers()
    {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT index_code, index_name, primary_etf_code, primary_etf_name, candidate_etf_codes, candidate_etf_names " +
                        "FROM quant_index_etf_mapping WHERE status = 1 ORDER BY index_code ASC");
        int sortOrder = 10;
        for (Map<String, Object> row : rows)
        {
            String indexCode = String.valueOf(row.get("index_code"));
            upsertPoolMember(
                    "INDEX_ETF_DEFAULT",
                    String.valueOf(row.get("primary_etf_code")),
                    row.get("primary_etf_name") == null ? null : String.valueOf(row.get("primary_etf_name")),
                    "etf",
                    "INDEX_PRIMARY_ETF",
                    "INCLUDED",
                    indexCode,
                    "PRIMARY",
                    String.valueOf(row.get("index_name")),
                    sortOrder);
            sortOrder += 10;
            List<Object> candidateCodes = parseJsonArray(row.get("candidate_etf_codes"));
            List<Object> candidateNames = parseJsonArray(row.get("candidate_etf_names"));
            for (int index = 0; index < candidateCodes.size(); index++)
            {
                String code = String.valueOf(candidateCodes.get(index));
                String name = index < candidateNames.size() ? String.valueOf(candidateNames.get(index)) : null;
                upsertPoolMember(
                        "INDEX_ETF_DEFAULT",
                        code,
                        name,
                        "etf",
                        "INDEX_CANDIDATE_ETF",
                        "CANDIDATE",
                        indexCode,
                        "CANDIDATE",
                        String.valueOf(row.get("index_name")),
                        sortOrder);
                sortOrder += 10;
            }
        }
    }

    private void insertPool(
            String poolCode,
            String poolName,
            String poolType,
            String scopeType,
            String description,
            Map<String, Object> ruleDefinition,
            int sortOrder)
    {
        jdbcTemplate.update(
                "INSERT INTO quant_symbol_pool (" +
                        "pool_code, pool_name, pool_type, scope_type, description, rule_definition, status, version_no, sort_order, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), 1, 1, ?, NOW(), NOW())",
                poolCode,
                poolName,
                poolType,
                scopeType,
                description,
                JSON.toJSONString(ruleDefinition),
                sortOrder);
    }

    private void upsertPoolMember(
            String poolCode,
            String stockCode,
            String stockName,
            String assetType,
            String sourceType,
            String inclusionStatus,
            String mappedIndexCode,
            String mappedRole,
            String note,
            int sortOrder)
    {
        jdbcTemplate.update(
                "INSERT INTO quant_symbol_pool_member (" +
                        "pool_code, stock_code, stock_name, asset_type, source_type, inclusion_status, mapped_index_code, mapped_role, note, sort_order, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW()) " +
                        "ON CONFLICT (pool_code, stock_code, inclusion_status, mapped_role) DO UPDATE SET " +
                        "stock_name = EXCLUDED.stock_name, asset_type = EXCLUDED.asset_type, source_type = EXCLUDED.source_type, " +
                        "mapped_index_code = EXCLUDED.mapped_index_code, note = EXCLUDED.note, sort_order = EXCLUDED.sort_order, update_time = NOW()",
                poolCode,
                normalizeCode(stockCode),
                stockName,
                assetType,
                sourceType,
                inclusionStatus,
                mappedIndexCode,
                mappedRole == null || mappedRole.isBlank() ? "NA" : mappedRole,
                note,
                sortOrder);
    }

    private String defaultPoolCodeForType(String scopeType)
    {
        try
        {
            return jdbcTemplate.queryForObject(
                    "SELECT pool_code FROM quant_symbol_pool WHERE scope_type = ? AND status = 1 ORDER BY sort_order ASC, pool_code ASC LIMIT 1",
                    String.class,
                    scopeType);
        }
        catch (DataAccessException ex)
        {
            return null;
        }
    }

    private List<String> loadPoolIncludedSymbols(String poolCode)
    {
        return jdbcTemplate.query(
                "SELECT stock_code FROM quant_symbol_pool_member " +
                        "WHERE pool_code = ? AND inclusion_status = 'INCLUDED' ORDER BY sort_order ASC, stock_code ASC",
                (rs, rowNum) -> rs.getString(1),
                poolCode);
    }

    private List<String> loadAllStockSymbols()
    {
        return jdbcTemplate.query(
                "SELECT stock_code FROM stock_basic " +
                        "WHERE COALESCE(is_st, 0) = 0 AND NOT (UPPER(COALESCE(industry, '')) = 'ETF' OR UPPER(COALESCE(stock_name, '')) LIKE '%ETF%') " +
                        "ORDER BY stock_code ASC",
                (rs, rowNum) -> rs.getString(1));
    }

    private Map<String, Object> buildPoolMemberSummary(List<Map<String, Object>> members)
    {
        int includedCount = 0;
        int excludedCount = 0;
        int candidateCount = 0;
        int stockCount = 0;
        int etfCount = 0;
        for (Map<String, Object> member : members)
        {
            String inclusionStatus = String.valueOf(member.get("inclusion_status"));
            if ("INCLUDED".equalsIgnoreCase(inclusionStatus))
            {
                includedCount++;
            }
            else if ("EXCLUDED".equalsIgnoreCase(inclusionStatus))
            {
                excludedCount++;
            }
            else if ("CANDIDATE".equalsIgnoreCase(inclusionStatus))
            {
                candidateCount++;
            }
            String assetType = String.valueOf(member.get("asset_type"));
            if ("etf".equalsIgnoreCase(assetType))
            {
                etfCount++;
            }
            else
            {
                stockCount++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("includedCount", includedCount);
        result.put("excludedCount", excludedCount);
        result.put("candidateCount", candidateCount);
        result.put("stockCount", stockCount);
        result.put("etfCount", etfCount);
        return result;
    }

    private Map<String, Object> scopeOption(String scopeType, String label, String description, boolean supportsConstraints, boolean needsPool)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scopeType", scopeType);
        result.put("label", label);
        result.put("description", description);
        result.put("supportsConstraints", supportsConstraints);
        result.put("needsPool", needsPool);
        return result;
    }

    private Map<String, Object> constraintOption(String field, String label, String description)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", field);
        result.put("label", label);
        result.put("description", description);
        return result;
    }

    private static Map<String, Object> mappingSeed(
            String indexCode,
            String indexName,
            String primaryEtfCode,
            String primaryEtfName,
            List<String> candidateEtfCodes,
            List<String> candidateEtfNames)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indexCode", indexCode);
        result.put("indexName", indexName);
        result.put("primaryEtfCode", primaryEtfCode);
        result.put("primaryEtfName", primaryEtfName);
        result.put("candidateEtfCodes", candidateEtfCodes);
        result.put("candidateEtfNames", candidateEtfNames);
        return result;
    }

    private String normalizeScopeType(String scopeType)
    {
        if (scopeType == null || scopeType.isBlank())
        {
            return "all_stocks";
        }
        String normalized = scopeType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized)
        {
            case "stock_pool", "etf_pool", "index_mapped_etf_pool" -> normalized;
            default -> "all_stocks";
        };
    }

    private String normalizePoolCode(String poolCode)
    {
        if (poolCode == null || poolCode.isBlank())
        {
            return null;
        }
        return poolCode.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> normalizeSymbols(List<String> symbols)
    {
        if (symbols == null || symbols.isEmpty())
        {
            return List.of();
        }
        Set<String> resolved = new LinkedHashSet<>();
        for (String item : symbols)
        {
            String code = normalizeCode(item);
            if (code != null)
            {
                resolved.add(code);
            }
        }
        return resolved.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private String normalizeCode(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        String digits = value.trim();
        if (digits.contains(","))
        {
            throw new ServiceException("symbol lists should be split before resolveScopeSymbols");
        }
        return String.format(Locale.ROOT, "%06d", Integer.parseInt(digits));
    }

    private Map<String, Object> parseJsonObject(Object raw)
    {
        if (raw == null)
        {
            return Map.of();
        }
        try
        {
            Object parsed = JSON.parse(String.valueOf(raw));
            if (parsed instanceof Map<?, ?> map)
            {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
        }
        catch (Exception ignored)
        {
            // ignore malformed json payload
        }
        return Map.of();
    }

    private List<Object> parseJsonArray(Object raw)
    {
        if (raw == null)
        {
            return List.of();
        }
        try
        {
            Object parsed = JSON.parse(String.valueOf(raw));
            if (parsed instanceof List<?> list)
            {
                return new ArrayList<>(list);
            }
        }
        catch (Exception ignored)
        {
            // ignore malformed json payload
        }
        return List.of();
    }

    private Map<String, Object> toCamelCaseMap(Map<String, Object> source)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet())
        {
            result.put(toCamelCase(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private String toCamelCase(String key)
    {
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char c : key.toCharArray())
        {
            if (c == '_')
            {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return builder.toString();
    }

    private int toInt(Object value)
    {
        if (value == null)
        {
            return 0;
        }
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
