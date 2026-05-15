-- Quant Road RuoYi menu + permission bootstrap (PostgreSQL)
-- Usage:
--   psql -h <host> -U <user> -d <db-quant> -f sql/ruoyi_quant_menu.sql
--
-- Idempotent design:
--   - safe to execute multiple times
--   - inserts missing records, updates core fields of existing records
--   - binds menus/permissions to admin role (role_key = 'admin')

BEGIN;

UPDATE sys_menu
   SET visible = '1',
       update_by = 'admin',
       update_time = NOW(),
       remark = CASE
                    WHEN menu_name = '若依官网' THEN '若依官网地址（主链默认隐藏）'
                    WHEN menu_name = '系统监控' THEN '系统监控目录（主链默认隐藏）'
                    WHEN menu_name = '系统工具' THEN '系统工具目录（主链默认隐藏）'
                    ELSE remark
                END
 WHERE (parent_id = 0 AND path IN ('monitor', 'tool'))
    OR (parent_id = 0 AND path = 'http://ruoyi.vip');

DO
$$
DECLARE
    v_quant_root_id bigint;
    v_dashboard_menu_id bigint;
    v_shadow_menu_id bigint;
    v_jobs_menu_id bigint;
    v_operations_menu_id bigint;
    v_execution_menu_id bigint;
    v_symbols_menu_id bigint;
    v_backtest_menu_id bigint;
    v_data_query_btn_id bigint;
    v_job_run_btn_id bigint;
    v_admin_role_id bigint;
BEGIN
    -- 1) Root directory: 量化分析 (/quant)
    SELECT menu_id
      INTO v_quant_root_id
      FROM sys_menu
     WHERE parent_id = 0
       AND path = 'quant'
       AND menu_type = 'M'
     ORDER BY menu_id
     LIMIT 1;

    IF v_quant_root_id IS NULL THEN
        INSERT INTO sys_menu
            (menu_name, parent_id, order_num, path, component, "query", route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
        VALUES
            ('量化分析', 0, 90, 'quant', 'Layout', NULL, 'Quant', '1', '0', 'M', '0', '0', '', 'chart', 'admin', NOW(), 'Quant Road 顶级目录')
        RETURNING menu_id INTO v_quant_root_id;
    ELSE
        UPDATE sys_menu
           SET menu_name = '量化分析',
               order_num = 90,
               component = 'Layout',
               route_name = 'Quant',
               is_frame = '1',
               is_cache = '0',
               visible = '0',
               status = '0',
               icon = 'chart',
               update_by = 'admin',
               update_time = NOW(),
               remark = 'Quant Road 顶级目录'
         WHERE menu_id = v_quant_root_id;
    END IF;

    -- 2) Child menu: 量化看板 (/quant/dashboard)
    SELECT menu_id
      INTO v_dashboard_menu_id
      FROM sys_menu
     WHERE parent_id = v_quant_root_id
       AND path = 'dashboard'
       AND menu_type = 'C'
     ORDER BY menu_id
     LIMIT 1;

    IF v_dashboard_menu_id IS NULL THEN
        INSERT INTO sys_menu
            (menu_name, parent_id, order_num, path, component, "query", route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
        VALUES
            ('量化看板', v_quant_root_id, 1, 'dashboard', 'quant/dashboard/index', NULL, 'QuantDashboard', '1', '0', 'C', '0', '0', '', 'dashboard', 'admin', NOW(), 'Quant 看板页面')
        RETURNING menu_id INTO v_dashboard_menu_id;
    ELSE
        UPDATE sys_menu
           SET menu_name = '量化看板',
               order_num = 1,
               component = 'quant/dashboard/index',
               route_name = 'QuantDashboard',
               is_frame = '1',
               is_cache = '0',
               visible = '0',
               status = '0',
               perms = '',
               icon = 'dashboard',
               update_by = 'admin',
               update_time = NOW(),
               remark = 'Quant 看板页面'
         WHERE menu_id = v_dashboard_menu_id;
    END IF;

    -- 3) Child menu: 影子对比 (/quant/shadow)
    SELECT menu_id
      INTO v_shadow_menu_id
      FROM sys_menu
     WHERE parent_id = v_quant_root_id
       AND path = 'shadow'
       AND menu_type = 'C'
     ORDER BY menu_id
     LIMIT 1;

    IF v_shadow_menu_id IS NULL THEN
        INSERT INTO sys_menu
            (menu_name, parent_id, order_num, path, component, "query", route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
        VALUES
            ('影子对比', v_quant_root_id, 2, 'shadow', 'quant/shadow/index', NULL, 'QuantShadowCompare', '1', '0', 'C', '0', '0', '', 'chart', 'admin', NOW(), 'Quant 影子对比页面')
        RETURNING menu_id INTO v_shadow_menu_id;
    ELSE
        UPDATE sys_menu
           SET menu_name = '影子对比',
               order_num = 2,
               component = 'quant/shadow/index',
               route_name = 'QuantShadowCompare',
               is_frame = '1',
               is_cache = '0',
               visible = '0',
               status = '0',
               perms = '',
               icon = 'chart',
               update_by = 'admin',
               update_time = NOW(),
               remark = 'Quant 影子对比页面'
         WHERE menu_id = v_shadow_menu_id;
    END IF;

    -- 4) Child menu: 调度中心 (/quant/jobs)
    SELECT menu_id
      INTO v_jobs_menu_id
      FROM sys_menu
     WHERE parent_id = v_quant_root_id
       AND path = 'jobs'
       AND menu_type = 'C'
     ORDER BY menu_id
     LIMIT 1;

    IF v_jobs_menu_id IS NULL THEN
        INSERT INTO sys_menu
            (menu_name, parent_id, order_num, path, component, "query", route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
        VALUES
            ('调度中心', v_quant_root_id, 3, 'jobs', 'quant/jobs/index', NULL, 'QuantDispatchCenter', '1', '0', 'C', '0', '0', '', 'job', 'admin', NOW(), 'Quant 调度发起与历史观察中心')
        RETURNING menu_id INTO v_jobs_menu_id;
    ELSE
        UPDATE sys_menu
           SET menu_name = '调度中心',
               order_num = 3,
               component = 'quant/jobs/index',
               route_name = 'QuantDispatchCenter',
               is_frame = '1',
               is_cache = '0',
               visible = '0',
               status = '0',
               perms = '',
               icon = 'job',
               update_by = 'admin',
               update_time = NOW(),
               remark = 'Quant 调度发起与历史观察中心'
         WHERE menu_id = v_jobs_menu_id;
    END IF;

    -- 5) Child menu: 运维中心 (/quant/operations)
    SELECT menu_id
      INTO v_operations_menu_id
      FROM sys_menu
     WHERE parent_id = v_quant_root_id
       AND path = 'operations'
       AND menu_type = 'C'
     ORDER BY menu_id
     LIMIT 1;

    IF v_operations_menu_id IS NULL THEN
        INSERT INTO sys_menu
            (menu_name, parent_id, order_num, path, component, "query", route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
        VALUES
            ('运维中心', v_quant_root_id, 4, 'operations', 'quant/operations/index', NULL, 'QuantOperationsCenter', '1', '0', 'C', '0', '0', '', 'tool', 'admin', NOW(), 'Quant 运维恢复中心')
        RETURNING menu_id INTO v_operations_menu_id;
    ELSE
        UPDATE sys_menu
           SET menu_name = '运维中心',
               order_num = 4,
               component = 'quant/operations/index',
               route_name = 'QuantOperationsCenter',
               is_frame = '1',
               is_cache = '0',
               visible = '0',
               status = '0',
               perms = '',
               icon = 'tool',
               update_by = 'admin',
               update_time = NOW(),
               remark = 'Quant 运维恢复中心'
         WHERE menu_id = v_operations_menu_id;
    END IF;

    -- 6) Child menu: 执行回写 (/quant/execution)
    SELECT menu_id
      INTO v_execution_menu_id
      FROM sys_menu
     WHERE parent_id = v_quant_root_id
       AND path = 'execution'
       AND menu_type = 'C'
     ORDER BY menu_id
     LIMIT 1;

    IF v_execution_menu_id IS NULL THEN
        INSERT INTO sys_menu
            (menu_name, parent_id, order_num, path, component, "query", route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
        VALUES
            ('执行回写', v_quant_root_id, 5, 'execution', 'quant/execution/index', NULL, 'QuantExecutionCenter', '1', '0', 'C', '0', '0', '', 'edit', 'admin', NOW(), 'Quant 成交录入与导入')
        RETURNING menu_id INTO v_execution_menu_id;
    ELSE
        UPDATE sys_menu
           SET menu_name = '执行回写',
               order_num = 5,
               component = 'quant/execution/index',
               route_name = 'QuantExecutionCenter',
               is_frame = '1',
               is_cache = '0',
               visible = '0',
               status = '0',
               perms = '',
               icon = 'edit',
               update_by = 'admin',
               update_time = NOW(),
               remark = 'Quant 成交录入与导入'
         WHERE menu_id = v_execution_menu_id;
    END IF;

    -- 6) Permission button: quant:data:query
    SELECT menu_id
      INTO v_symbols_menu_id
      FROM sys_menu
     WHERE parent_id = v_quant_root_id
       AND path = 'symbols'
       AND menu_type = 'C'
     ORDER BY menu_id
     LIMIT 1;

    IF v_symbols_menu_id IS NULL THEN
        INSERT INTO sys_menu
            (menu_name, parent_id, order_num, path, component, "query", route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
        VALUES
            ('标的跟踪', v_quant_root_id, 6, 'symbols', 'quant/symbols/index', NULL, 'QuantSymbolTracker', '1', '0', 'C', '0', '0', '', 'guide', 'admin', NOW(), 'Quant 标的视图与执行跟踪')
        RETURNING menu_id INTO v_symbols_menu_id;
    ELSE
        UPDATE sys_menu
           SET menu_name = '标的跟踪',
               order_num = 6,
               component = 'quant/symbols/index',
               route_name = 'QuantSymbolTracker',
               is_frame = '1',
               is_cache = '0',
               visible = '0',
               status = '0',
               perms = '',
               icon = 'guide',
               update_by = 'admin',
               update_time = NOW(),
               remark = 'Quant 标的视图与执行跟踪'
         WHERE menu_id = v_symbols_menu_id;
    END IF;

    SELECT menu_id
      INTO v_backtest_menu_id
      FROM sys_menu
     WHERE parent_id = v_quant_root_id
       AND path = 'backtest'
       AND menu_type = 'C'
     ORDER BY menu_id
     LIMIT 1;

    IF v_backtest_menu_id IS NULL THEN
        INSERT INTO sys_menu
            (menu_name, parent_id, order_num, path, component, "query", route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
        VALUES
            ('回测分析', v_quant_root_id, 7, 'backtest', 'quant/backtest/index', NULL, 'QuantBacktestAnalysis', '1', '0', 'C', '0', '0', '', 'chart', 'admin', NOW(), 'Quant 回测结果与策略治理分析')
        RETURNING menu_id INTO v_backtest_menu_id;
    ELSE
        UPDATE sys_menu
           SET menu_name = '回测分析',
               order_num = 7,
               component = 'quant/backtest/index',
               route_name = 'QuantBacktestAnalysis',
               is_frame = '1',
               is_cache = '0',
               visible = '0',
               status = '0',
               perms = '',
               icon = 'chart',
               update_by = 'admin',
               update_time = NOW(),
               remark = 'Quant 回测结果与策略治理分析'
         WHERE menu_id = v_backtest_menu_id;
    END IF;

    -- 6) Permission button: quant:data:query
    SELECT menu_id
      INTO v_data_query_btn_id
      FROM sys_menu
     WHERE parent_id = v_dashboard_menu_id
       AND menu_type = 'F'
       AND perms = 'quant:data:query'
     ORDER BY menu_id
     LIMIT 1;

    IF v_data_query_btn_id IS NULL THEN
        INSERT INTO sys_menu
            (menu_name, parent_id, order_num, path, component, "query", route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
        VALUES
            ('数据查询', v_dashboard_menu_id, 1, '#', '', NULL, NULL, '1', '0', 'F', '0', '0', 'quant:data:query', '#', 'admin', NOW(), 'Quant 数据查询权限')
        RETURNING menu_id INTO v_data_query_btn_id;
    ELSE
        UPDATE sys_menu
           SET menu_name = '数据查询',
               order_num = 1,
               path = '#',
               component = '',
               is_frame = '1',
               is_cache = '0',
               visible = '0',
               status = '0',
               perms = 'quant:data:query',
               icon = '#',
               update_by = 'admin',
               update_time = NOW(),
               remark = 'Quant 数据查询权限'
         WHERE menu_id = v_data_query_btn_id;
    END IF;

    -- 7) Permission button: quant:job:run
    SELECT menu_id
      INTO v_job_run_btn_id
      FROM sys_menu
     WHERE parent_id = v_shadow_menu_id
       AND menu_type = 'F'
       AND perms = 'quant:job:run'
     ORDER BY menu_id
     LIMIT 1;

    IF v_job_run_btn_id IS NULL THEN
        INSERT INTO sys_menu
            (menu_name, parent_id, order_num, path, component, "query", route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
        VALUES
            ('任务执行', v_shadow_menu_id, 1, '#', '', NULL, NULL, '1', '0', 'F', '0', '0', 'quant:job:run', '#', 'admin', NOW(), 'Quant 任务执行权限')
        RETURNING menu_id INTO v_job_run_btn_id;
    ELSE
        UPDATE sys_menu
           SET menu_name = '任务执行',
               order_num = 1,
               path = '#',
               component = '',
               is_frame = '1',
               is_cache = '0',
               visible = '0',
               status = '0',
               perms = 'quant:job:run',
               icon = '#',
               update_by = 'admin',
               update_time = NOW(),
               remark = 'Quant 任务执行权限'
         WHERE menu_id = v_job_run_btn_id;
    END IF;

    -- 8) Bind all new menus/permissions to admin role
    SELECT role_id
      INTO v_admin_role_id
      FROM sys_role
     WHERE role_key = 'admin'
       AND status = '0'
       AND del_flag = '0'
     ORDER BY role_id
     LIMIT 1;

    IF v_admin_role_id IS NOT NULL THEN
        INSERT INTO sys_role_menu (role_id, menu_id)
        SELECT v_admin_role_id, x.menu_id
          FROM (VALUES
                    (v_quant_root_id),
                    (v_dashboard_menu_id),
                    (v_shadow_menu_id),
                    (v_jobs_menu_id),
                    (v_operations_menu_id),
                    (v_execution_menu_id),
                    (v_data_query_btn_id),
                    (v_job_run_btn_id)
               ) AS x(menu_id)
         WHERE x.menu_id IS NOT NULL
           AND NOT EXISTS (
                SELECT 1
                  FROM sys_role_menu rm
                 WHERE rm.role_id = v_admin_role_id
                   AND rm.menu_id = x.menu_id
           );
    END IF;
END
$$;

COMMIT;

-- Quick verification queries (optional):
-- SELECT menu_id, parent_id, menu_name, path, component, menu_type, perms FROM sys_menu WHERE path IN ('quant','dashboard','shadow','jobs','execution') OR perms IN ('quant:data:query','quant:job:run') ORDER BY parent_id, order_num, menu_id;
-- SELECT rm.role_id, r.role_key, rm.menu_id, m.menu_name, m.perms FROM sys_role_menu rm JOIN sys_role r ON r.role_id = rm.role_id JOIN sys_menu m ON m.menu_id = rm.menu_id WHERE r.role_key = 'admin' AND (m.path IN ('quant','dashboard','shadow','jobs','execution') OR m.perms IN ('quant:data:query','quant:job:run')) ORDER BY rm.menu_id;
