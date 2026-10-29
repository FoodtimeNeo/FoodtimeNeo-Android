# 本机 Backend 联通

应用现在默认访问 `https://154.9.252.201/api/v1/`，不需要端口映射。本页只描述切换到专用本地Backend的联调方式。

本地目标地址为 `http://127.0.0.1:8080/api/v1/`，构建时设置 `-PFOODTIME_API_BASE_URL=http://127.0.0.1:8080/api/v1/`。Android 的127.0.0.1指向设备自身，需要用ADB端口反向映射连到开发电脑：

```powershell
adb reverse tcp:8080 tcp:8080
adb reverse --list
```

Backend 在电脑上监听8080；本地联调包请求仍使用127.0.0.1。重启设备或重新连接ADB后需重新设置映射。HTTP仅放行本机和指定服务器，其他部署地址使用HTTPS。

`data/remote/HttpTransport` 负责实际HTTP、Cookie及CSRF请求头，限制请求在同一个配置来源，不自动跟随重定向，不自动重试写操作。业务JSON和安全错误由远程仓库解释。所有网络请求必须在后台线程运行；domain不依赖Android，presentation不直接引用data。

默认应用已装配RemoteRepository，认证、浏览、投稿、个人记录与管理页面均走真实接口。Backend样例仅在其local-integration配置显式启用时生成；设备验收记录见verification.md，本地模拟结果不作为已联通的证据。

完整构建属性、独立包名、不同主机端口映射和设备验收脚本说明见 [构建与测试](build-and-test.md)。该脚本的 `-Device` 模式固定连接专用本机8080服务，不把线上库当作写入测试库。连接或图片异常时，先核对设备的实际构建地址、ADB映射和Backend媒体文件，不依靠历史截图推断当前服务正常。
