# 头像裁剪与上传

头像原图最大10MB（10×1024×1024字节，含边界），支持JPEG/PNG。Web与Android均先显示固定1:1选框，支持拖动、缩放、旋转与重置，无方向按钮；确认后输出512×512 JPEG，保存资料才上传。取消保留原头像，保存失败保留草稿。

POST /api/v1/uploads/avatars要求登录和CSRF，复用上传限流与存储预算；输入文件超过10MB拒绝。后端解码验证图片，校验正方形后统一重新编码为512×512 JPEG并移除元数据。PUT /api/v1/users/me/profile设置新引用时再次检查本人归属、实际体积与512×512尺寸，不能通过通用图片上传绕过。仅修改昵称时允许保留历史头像。

通用图片50MB上传规则保持最新开发分支原有行为。头像功能不会放宽帖子配图限制。客户端仍保留2000万像素预检以限制解码内存。

检查：Web npm run check；Android verifyArchitecture testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest；后端 mvnw -B -ntp -Pintegration verify。AvatarCropRunner为Android图片编解码设备测试，需独立测试设备；没有连接设备时只编译该测试，不报告为已运行。