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
sequenceDiagram
    autonumber
    participant O as OP 管理员
    participant Cmd as 命令处理器
    participant Map as UUID 颜色映射
    participant Tmp as config.yml.tmp
    participant Store as config.yml
    participant P as 被设置的玩家
    participant Event as AsyncChatEvent
    participant Renderer as ChatRenderer
    participant Chat as 游戏聊天栏

    Note over Cmd,Store: 插件启动时读取 config.yml，验证 UUID 和颜色名后加载到并发安全映射

    O->>Cmd: /mc-select-player-chat-color 玩家 颜色或 reset
    Cmd->>Cmd: 校验 mc-select-player-chat-color.admin 权限
    alt 没有权限
        Cmd-->>O: 返回无权限提示
    else 参数数量不是两个
        Cmd-->>O: 返回命令用法和全部 16 种颜色
    else 参数格式正确
        Cmd->>Cmd: 先精确查找在线玩家
        alt 玩家在线
            Cmd->>P: 取得在线玩家 UUID
        else 玩家不在线
            Cmd->>Cmd: 从服务端缓存查找 OfflinePlayer
            alt 未缓存或不存在
                Cmd-->>O: 返回找不到玩家提示
            else 已缓存
                Cmd->>P: 取得已缓存玩家 UUID
            end
        end

        alt 找到目标玩家
            alt 第二参数是 reset
                Cmd->>Map: 移除目标 UUID 的颜色
                alt 原本没有颜色
                    Cmd-->>O: 提示该玩家没有设置颜色
                else 原本存在颜色
                    Cmd->>Tmp: 写入完整 UUID 到颜色映射
                    Tmp->>Store: 优先原子移动替换，不支持时普通替换 config.yml
                    alt 写入成功
                        Store-->>Cmd: 持久化成功
                        Cmd-->>O: 确认已恢复默认聊天颜色
                    else 写入失败
                        Tmp-->>Cmd: 记录异常并清理临时文件
                        Cmd-->>O: 提示本次运行已生效但未持久化
                    end
                end
            else 第二参数是颜色名
                Cmd->>Cmd: 转小写后在 NamedTextColor.NAMES 查找
                alt 不是 16 种原版命名颜色之一
                    Cmd-->>O: 返回不支持颜色、用法和颜色列表
                else 是有效颜色
                    Cmd->>Map: 用目标 UUID 写入 NamedTextColor
                    Cmd->>Tmp: 写入完整 UUID 到颜色映射
                    Tmp->>Store: 优先原子移动替换，不支持时普通替换 config.yml
                    alt 写入成功
                        Store-->>Cmd: 持久化成功
                        Cmd-->>O: 用所选颜色显示设置确认
                    else 写入失败
                        Tmp-->>Cmd: 记录异常并清理临时文件
                        Cmd-->>O: 提示本次运行已生效但未持久化
                    end
                end
            end
        end
    end

    P->>Event: 发送签名或普通聊天消息
    Event->>Event: HIGHEST 优先级，忽略已取消事件
    Event->>Map: 按发送者 UUID 查询 NamedTextColor
    alt UUID 没有颜色映射
        Event-->>Chat: 不设置自定义 Renderer，Paper 默认格式继续生效
    else UUID 有颜色映射
        Event->>Event: 注册 viewerUnaware ChatRenderer
        Note over Renderer,Chat: 所有接收者看到相同格式，不依赖接收者身份
        Renderer->>Renderer: 将消息 Component 转纯文本
        Renderer->>Renderer: 组成 <玩家名> 消息，并为整个 Component 设置同一种颜色
        Renderer-->>Chat: Paper 将完整同色 Component 分发给所有聊天接收者
    end
```

颜色映射由 UUID 而非玩家名保存，因此玩家改名后仍会保留已设置的聊天颜色。

## 环境与构建

本项目面向 Paper `26.2.build.87-stable` 与 Java 25，使用 Paper 的 `AsyncChatEvent` 和 `ChatRenderer`。不保证旧版 Paper、Spigot 或 Purpur 的兼容性。

```bash
mvn clean package
```

构建产物为 `target/mc-select-player-chat-color-1.0.0.jar`。将它放入服务器的 `plugins/` 目录后重启服务器即可加载。
