
2018年10月23日

云黑板（云便签）

简单C/S架构的云服务，用于在多个设备之间同步便签（短文本）。

服务端由Python3实现，分别在Windows（C#）和Android上实现了客户端。
已经实现了注册帐号、登录、下载（多个）便签、更新/上传便签、删除便签等功能。
服务端用json存储和访问所有用户的便签数据，C-S间的请求协议也基于json设计。
本项目考虑了一些安全特性，例如使用RC4算法加密服务器数据和会话连接，以及用挑战响应方式认证用户密码，并用密码保护会话密钥的分发。

