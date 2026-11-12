package io.github.connectcolours;

public enum Feature {
    SIDEBAR("sidebar", "返回手势呼出侧边栏", "从左右边缘滑到最大位移并保持设定时间，震动后松手呼出同侧智能侧边栏，回划取消。桌面短滑回主屏，需勾选系统框架、系统界面与智能侧边栏作用域。"),
    SCREENSHOT("screenshot", "三键截图选项", "在截屏设置中添加三键截图开关，与双键截图互斥。需勾选系统框架和截屏作用域。"),
    TRAFFIC("traffic", "显示今日流量", "将移动数据磁贴标题替换为今日移动流量。"),
    BLUETOOTH("bluetooth", "蓝牙设备尝试重连选项", "在已配对设备页面提供自动连接和重试次数设置。每台设备默认关闭。"),
    BLUETOOTH_PROMPTS("bluetooth_prompts", "蓝牙重连提示", "尝试重连、重连成功或次数用尽时显示短暂文字提示。"),
    UPDATE_ENTRIES("update_entries", "隐藏系统更新入口", "隐藏设置内的更新卡片、入口和搜索结果。"),
    UPDATE_PROMPTS("update_prompts", "屏蔽系统更新提示", "屏蔽已适配的更新弹窗和通知。"),
    UPDATE_DOTS("update_dots", "隐藏系统更新红点", "移除关于本机的更新提示红点。"),
    UPDATE_NETWORK("update_network", "屏蔽系统更新联网", "阻止软件更新应用查询和下载更新。"),
    UPDATE_STATE("update_state", "显示为无新版本", "让软件更新应用报告没有新版本。"),
    SIM_WELCOME("sim_welcome", "屏蔽运营商欢迎弹窗", "关闭 SIM 工具包中“感谢您使用…”的欢迎提示。");

    public static final String PREFS = "features";
    public final String key;
    public final String title;
    public final String description;

    Feature(String key, String title, String description) {
        this.key = key;
        this.title = title;
        this.description = description;
    }
}
