# 首版验证记录

日期：2026-10-16（Asia/Shanghai）

## 通过

- WSL Ubuntu / JDK 17 / Android SDK 35 / Gradle 8.9 工具链安装完成。
- `testDebugUnitTest`：9 个测试，0 失败、0 错误。
- `assembleDebug`：自用调试签名 APK 构建成功。
- `assembleDebugAndroidTest`：数据库测试与五页面冒烟测试已编译。
- `lintDebug`：No issues found。
- `apksigner verify`：APK v2 签名校验通过。
- 实际访问天天基金 `110020` 的公开数据，验证基金代码、名称和历史净值字段存在。不是正式签约接口或稳定性承诺。

## 未通过或未完成

- WSL 的 `/dev/kvm` 不可用，加载 `kvm_intel` 返回 Operation not supported。
- Android 35 软件模拟器可连接 ADB，但首次启动超过测试等待期限，系统日志显示 Watchdog 因主线程阻塞 68 秒终止系统进程，随后重复启动。
- 未完成安卓数据库仪器测试、五页面运行测试和截图 QA；没有将它们计为通过。
- 未做真机安装、支付宝实际份额对账、后台省电兼容性、长时间网络稳定性测试。
- 曾尝试延长测试模拟器 Watchdog 超时，settings 服务返回 Broken pipe；未确认写入成功。该尝试不涉及用户手机或 Windows 设置。

## 后续验证入口

完整仪器测试：在可正常启动的模拟器或开发测试手机上运行 `bash gradlew connectedDebugAndroidTest`。测试使用独立的 `test_` 数据库前缀；App 冒烟测试会打开界面，但不会添加或删除真实持仓。

无硬件加速时可通过 `tools/start-emulator.sh` 重试，但软件模拟首启很慢。运行 `tools/test-emulator.sh` 会重新构建、等待测试模拟器启动、安装测试包并执行测试，超时会失败退出。

## 已知产品范围

- 定投按计划自动生成，实际扣款无法查询。支持逐笔确认或按总持仓批量核对；汇总核对保留逐笔估算标记。
- 仅内置 2026 年国内休市日历；其他年份和基金特殊开放日需要核对。
- 配置分析按手动分类和行业标签计算，不进行底层持仓穿透。
- 尚未接入指数估值、经理变更、持仓重合或择时回测，当前建议为配置规则提醒。
- 备份未加密，卸载会清除本机账本。请先导出备份并妥善保存。
