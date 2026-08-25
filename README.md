# mc-select-player-chat-color

为 Paper 服务器中的指定玩家设置原版聊天颜色。设置后，玩家发送的整条聊天内容，包括 `<玩家名>` 与消息正文，都会使用同一种颜色。

## 使用方法

OP 或拥有 `mc-select-player-chat-color.admin` 权限的管理员可以执行：

```text
/mc-select-player-chat-color <玩家> <颜色|reset>
```

示例：

```text
/mc-select-player-chat-color rainyxin yellow
/mc-select-player-chat-color rainyxin dark_aqua
/mc-select-player-chat-color rainyxin reset
```

在线玩家可直接设置。离线玩家必须已经被当前服务端缓存，以确保颜色绑定到正确的 UUID。`reset` 会恢复该玩家的默认聊天样式。

支持全部 16 种原版命名颜色：

```text
black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, gray,
dark_gray, blue, green, aqua, red, light_purple, yellow, white
```

## 工作原理

```mermaid
classDiagram
    direction TB

    class 聊天颜色插件主类 {
        - 玩家 UUID 到颜色的并发映射
        - 可用原版颜色名称列表
        + 启用插件()
        + 处理异步聊天事件(事件)
        + 执行管理员命令(发送者, 参数)
        + 提供命令补全(发送者, 参数)
        - 查找在线或已缓存玩家(名称)
        - 加载玩家颜色()
        - 保存玩家颜色()
        - 发送命令用法(发送者)
    }

    class JavaPlugin抽象父类 {
        <<抽象父类>>
        + 保存默认配置()
        + 取得配置()
        + 取得服务器()
    }

    class 事件监听器 {
        <<接口>>
    }

    class 命令执行器 {
        <<接口>>
        + 执行命令(发送者, 命令, 参数)
    }

    class 命令补全器 {
        <<接口>>
        + 提供补全(发送者, 命令, 参数)
    }

    class 玩家颜色映射 {
        <<并发哈希映射>>
        玩家 UUID
        原版命名颜色
        + 查询(UUID)
        + 写入(UUID, 颜色)
        + 移除(UUID)
    }

    class 配置文件 {
        <<config.yml>>
        玩家 UUID 到颜色名称
        + 读取()
        + 写入临时文件()
        + 原子替换或普通替换()
    }

    class 原版命名颜色 {
        <<Adventure 值对象>>
        原版颜色名称索引
        + 按名称查询(颜色名)
        + 取得颜色名称()
    }

    class 异步聊天事件 {
        发送者 UUID
        聊天消息组件
        + 设置聊天渲染器(渲染器)
    }

    class 聊天渲染器 {
        <<Paper 接口>>
        + 创建无接收者差异渲染器(渲染器)
        + 渲染(发送者, 显示名, 消息, 接收者)
    }

    class 纯文本组件序列化器 {
        <<Adventure 工具>>
        + 转换为纯文本(消息)
    }

    class 在线玩家 {
        UUID 唯一标识
        玩家名
    }

    class 已缓存离线玩家 {
        UUID 唯一标识
        玩家名
    }

    JavaPlugin抽象父类 <|-- 聊天颜色插件主类
    事件监听器 <|.. 聊天颜色插件主类
    命令执行器 <|.. 聊天颜色插件主类
    命令补全器 <|.. 聊天颜色插件主类

    聊天颜色插件主类 *-- 玩家颜色映射 : 持有
    聊天颜色插件主类 --> 配置文件 : 读取并安全保存
    配置文件 --> 玩家颜色映射 : 持久化 UUID 映射
    玩家颜色映射 --> 原版命名颜色 : 存储颜色值
    聊天颜色插件主类 --> 原版命名颜色 : 校验颜色名称

    聊天颜色插件主类 ..> 在线玩家 : 精确查找在线玩家
    聊天颜色插件主类 ..> 已缓存离线玩家 : 查找已缓存离线玩家
    聊天颜色插件主类 --> 异步聊天事件 : 以 HIGHEST 优先级处理
    异步聊天事件 --> 聊天渲染器 : UUID 有颜色时设置
    聊天渲染器 --> 纯文本组件序列化器 : 展平消息文本
    聊天渲染器 --> 原版命名颜色 : 为整条聊天设置颜色
```

颜色映射由 UUID 而非玩家名保存，因此玩家改名后仍会保留已设置的聊天颜色。

## 环境与构建

本项目面向 Paper `26.2.build.87-stable` 与 Java 25，使用 Paper 的 `AsyncChatEvent` 和 `ChatRenderer`。不保证旧版 Paper、Spigot 或 Purpur 的兼容性。

```bash
mvn clean package
```

构建产物为 `target/mc-select-player-chat-color-1.0.0.jar`。将它放入服务器的 `plugins/` 目录后重启服务器即可加载。
