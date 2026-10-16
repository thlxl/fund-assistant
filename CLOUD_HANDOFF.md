# 基金手账：云端开发交接

## 当前项目

安卓个人基金工具，原生 Java + SQLite，包名 `cn.local.fund`，Android 8.0 及以上。
源码原本位于 Windows `D:\private_project\fund-assistant`，使用 Ubuntu WSL2 构建。

已实现：手动添加持仓、公开净值更新、每日交易日固定金额定投自动补记与去重、逐笔核对与按总持仓批量核对、配置比例及定投资金预算提醒、JSON 备份恢复、CSV 导出。

详细口径见 README.md；已通过和未完成的检查见 VALIDATION.md。不能把编译成功说成真机测试成功。

## 用户后续反馈

用户询问了「剩余持仓总成本」和「起始持仓日期」含义。当前代码尚未改名：

- 总成本是剩余份额的成本，不是当前市值；支付宝持有收益的具体口径需核对。
- 起始持仓日期是录入持仓数据截至日期，不是首次买入或确认日期；起始日期及以前的交易已经包含在快照里。
- 可考虑把字段改为「持仓数据截至日期」，并改善成本说明，但没有据此偷偷更改记账规则。

## 构建

JDK 17、Android SDK 35、AGP 8.7.3、Gradle 8.9。

云端 Linux 环境初始化命令：

```bash
bash tools/setup-cloud.sh
```

脚本缺少依赖时会尝试通过 apt-get 安装 JDK 17、curl、unzip，需要 root 或免密码 sudo。随后安装安卓 SDK，执行单元测试、APK 构建及静态检查。已有 JDK 须与 Gradle 8.9 / AGP 8.7.3 兼容。

在已有 JDK 17、curl、unzip 的 Linux 环境也可以分步执行：

```bash
bash tools/setup-wsl.sh
bash gradlew testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest
```

setup-wsl.sh 虽然以 WSL 命名，使用的是普通 Linux 路径与命令；会下载 SDK 和 Gradle，并接受 SDK 许可，需允许相关网络访问。它写入本机 local.properties，不应提交该文件。

官方云端环境的依赖、网络与权限仍需实际配置和测试，不能保证当前脚本直接适用于所有云端环境。未验证云端 KVM/模拟器支持，不保证迁移能解决仪器测试问题。

## 发布与数据

迁移的是源码与开发工作，不是用户手机里的持仓。源码包不包含真实持仓、私有数据库、导出账本或签名密钥。

现有自用 APK 用本机 WSL `~/.android/debug.keystore` 签名；该密钥没有打包。云端首次构建会使用不同密钥，可能无法覆盖安装当前 APK。若需保留覆盖升级能力，应单独通过受保护的凭证机制配置原签名密钥，不能提交到 Git 仓库。用户手机换包或卸载前应先备份。

## 功能边界

- 不接支付宝账户、密码或未公开接口；净值来自天天基金公开数据，失败保留缓存。
- 自动记录是预计成交，无法判断扣款是否真实成功；批量核对保留逐笔估算标记。
- 国内日历目前只覆盖 2026 年，基金特殊停购和境外假期需核对。
- 建议目前为配置规则，未接指数估值、经理变更、基金持仓穿透或择时回测。
- 不新增云端持仓同步、自动下单或收费服务，除非用户另行要求。

## 接入 Codex Cloud

1. 用户指定的仓库为 https://github.com/thlxl/fund-assistant 。只上传本项目源码与说明。
2. 在 ChatGPT/Codex 的云端环境设置中授权该仓库，配置安卓依赖及网络，运行构建检查后发布环境。
3. 从手机选择该云端环境开始任务，并提供本交接文件、README.md 与 VALIDATION.md 作为上下文。

当前本地聊天没有通过工具整体迁移。源码提交到仓库也不等于已创建或发布 Codex Cloud 环境；需在账号界面验证环境状态。
