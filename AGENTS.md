# 基金手账开发约定

先读 README.md、CLOUD_HANDOFF.md 和 VALIDATION.md，了解产品口径、已有反馈及验证边界。

- 保持原生 Java + SQLite；优先用已有代码和安卓标准 API，不为少量代码新增运行时依赖。
- 金额和份额计算使用 BigDecimal，保留交易校验、事务回滚与定投去重。
- 预计成交、逐笔确认与汇总核对必须明确区分。没有真实账户数据时不能伪造已成交或择时建议。
- 不能上传用户账本、数据库、签名密钥、凭证或 local.properties。dist/ 和构建缓存不纳入源码仓库。
- 非简单逻辑变更增加一个有意义的测试；不要把测试编译成功说成运行通过。
- 普通检查：`bash gradlew testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest`。
- 新云端 Linux 环境初始化：`bash tools/setup-cloud.sh`，需要网络、JDK 17 或更新的兼容 JDK，以及必要时的软件包安装权限。
- 安卓仪器测试需要真实可用的设备或模拟器，运行 `bash gradlew connectedDebugAndroidTest`；未验证时明确说明。
- 最终回复简洁，说明修改、验证及未检查的风险。
