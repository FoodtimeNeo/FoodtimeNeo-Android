# v1.1.0 发布说明

versionName为1.1.0，versionCode为6，包名仍为cn.edu.bjtu.foodtime，沿用原正式签名。通过dev → main发布PR合并后，由该main合并提交构建签名APK，Release标签与版本名统一为v1.1.0。

本次包含公告创建、编辑、当前公告设置及Markdown展示，管理员审核发布者身份与内容详情、数据看板、图片按需加载、登录注册密码遮罩及网络安全加固。正式包仅使用HTTPS，默认直接连接154.9.252.201。

Web下载制品取自本次main合并触发的正式CD产物，与GitHub Release中的app-release.apk逐字节一致；Web随后合并main。开发分支验收包不作为本次正式网站下载制品。

发布检查包括架构、JVM测试、Lint和构建；签名及版本由CD验证。此前的本地三端安全验收结果保存在联合工作区qa/security-review-20261031。main发布和APK发布不等于后端云服务部署。
