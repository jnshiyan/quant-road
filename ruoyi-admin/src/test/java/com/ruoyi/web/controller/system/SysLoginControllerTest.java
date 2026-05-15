package com.ruoyi.web.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.core.domain.entity.SysMenu;

class SysLoginControllerTest
{
    @Test
    void filterMenusForMainlineRemovesPlatformMenusAndKeepsQuantMenus()
    {
        SysMenu quantRoot = menu(1L, "量化分析", "/quant");
        quantRoot.setChildren(List.of(menu(11L, "量化看板", "dashboard")));

        SysMenu monitorRoot = menu(2L, "系统监控", "/monitor");
        monitorRoot.setChildren(List.of(menu(21L, "在线用户", "online")));

        SysMenu toolRoot = menu(3L, "系统工具", "/tool");
        toolRoot.setChildren(List.of(menu(31L, "代码生成", "gen")));

        SysMenu ruoyiSite = menu(4L, "若依官网", "http://ruoyi.vip");

        List<SysMenu> filtered = new SysLoginController().filterMenusForMainline(
                List.of(quantRoot, monitorRoot, toolRoot, ruoyiSite));

        assertEquals(1, filtered.size());
        assertEquals("/quant", filtered.get(0).getPath());
        assertEquals(1, filtered.get(0).getChildren().size());
        assertEquals("量化看板", filtered.get(0).getChildren().get(0).getMenuName());
    }

    private SysMenu menu(Long id, String name, String path)
    {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        menu.setMenuName(name);
        menu.setPath(path);
        menu.setMenuType("M");
        menu.setVisible("0");
        menu.setStatus("0");
        return menu;
    }
}
