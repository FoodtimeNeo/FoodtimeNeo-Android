# 本机 Backend 联通

目标地址为 `http://127.0.0.1:8080/api/v1`。Android 的127.0.0.1指向设备自身，需要用ADB端口反向映射连到开发电脑：

```powershell
adb reverse tcp:8080 tcp:8080
adb reverse --list
```

Backend 在电脑上监听8080；设备请求仍使用127.0.0.1，不改用模拟器网关。重启设备或重新连接ADB后需重新设置映射。开发HTTP限于本机；生产需配置HTTPS。

`data/remote/HttpTransport` 负责实际HTTP、Cookie及CSRF请求头，限制请求在同一个配置来源，不自动跟随重定向，不自动重试写操作。业务JSON和安全错误由远程仓库解释。所有网络请求必须在后台线程运行；domain不依赖Android，presentation不直接引用data。

默认应用已装配RemoteRepository，认证、浏览、投稿、个人记录与管理页面均走真实接口。Backend样例仅在其local-integration配置显式启用时生成；设备验收记录见verification.md，本地模拟结果不作为已联通的证据。
