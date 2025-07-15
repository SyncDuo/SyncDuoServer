# 设计文档

`该文档是 SyncDuoServer 项目的设计文档,包含了典型的用户旅程和前端接口`

## 设计背景
syncthing 加强版. syncthing 是一款开源的多终端文件同步工具,以文件夹为颗粒度,在多个终端间保持版本一致. 但是 syncthing 不支持 "忽略删除文件"
的操作(etc: A 终端空间不足删除了文件, 希望在 B 终端保留该文件), 如果用户需要备份功能, 
需要再集成单独的备份软件.

为了解决 syncthing 的局限性, SyncDuoServer 出现了. 关键特性是支持 "忽略删除文件" 特性.
更好地管理多个终端间的数据.

当前 SyncDuoServer 只能管理"本机"的数据,多终端间的同步依赖用户通过 syncthing 手动创建同步流, 后续会集成 syncthing 
和数据备份软件,在用户体验上做到一致.

## 核心流程
### NAS的文件夹监控
watcher -> filesystem event -> copyFile -> copyJob event -> copyJobStatus event -> copyJobStats event
### 定时器
syncflow list -> checkAndCopy -> copyJobEvent -> copyJobStatus event -> copyJobStats event
### 创建流

## 用户旅程
1. 创建流: 用户登录 SyncDuoServer 管理界面, 选择"备份"/"转换"等多种同步模式, 选择源文件夹和目的文件夹, 点击确定后即可完成创建.
2. 查看流: 用户登录 SyncDuoServer 管理界面, 可以看到所有流的状态和统计数据.
3. 管理流: 用户登录 SyncDuoServer 管理界面, 可以对流进行重命名/停止/删除/修改过滤条件的动作.
4. 修改系统信息: 用户登录 SyncDuoServer 管理界面, 可以修改系统的各种参数,例如备份频率,备份存储地址等.

## 前端 API
### 创建流
#### 使用场景
创建流
#### Endpoint
 `/syncflow/add-syncflow`
#### 结果(JSON)

### 文件夹路径搜索联想 API
#### 使用场景
创建流. 用户在输入框中输入任意字符, 格式符合 Linux 文件系统路径规范, 则系统给出文件夹路径联想结果, 支持用户选择联想结果作为文件夹路径.
#### Endpoint
`/filesystem/get-subfolders`
#### 参数
http 请求参数(RequestParam), 名称为 path, 类型为字符串
#### 结果(JSON)
```json
{
  "code": 200,
  "message": "xxxx",
  "hostName": "xxxx",
  "folderList": [
    {
      "folderName": "xxx",
      "folderFullPath": "xxx"
    }
  ]
}
```