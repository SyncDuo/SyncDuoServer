# 设计文档
## 基本用户故事
### 新建Flow -- 完成
1. 点击 "新建Flow" 按钮
2. 展示Json编辑器, 编排 "Node"
   1. 前端校验 json 完整性
   2. 后端校验 json 正确性(是否有序号, node是否定义存在)
3. 点击下一步, 配置所有 "Node" 需要的参数
   1. 后端返回依赖分析, 哪些参数需要配置, 哪些参数"由前置node提供". 前置node提供的参数预填充, 用户可以覆盖
4. 点击下一步, 设置 "Flow"的名字和运行的频率
   1. every x seconds/minutes/hours, 精确小数点后一位
   2. flow name 全局唯一, 需要查重
5. 点击保存

### 查看Flow -- 完成
1. 点击dashboard的Workflow目录
2. 展示workflow列表,包含 id, name, corn, status, next_run_date 四列. 详情,编辑,删除三个按钮

### 查看Flow详情 -- 未实现
1. 点击"详情"按钮
2. 展示flowchart 静态图
   1. 每个节点展示参数
   2. 一句话总结展示任务流的作用(AI?)
3. 支持在 flowchart 上直接修改参数, 但不能修改节点连接
4. 支持在 flowchart 上直接修改 cron, 但不能修改节点连接

### 删除Flow -- 完成
1. 点击"删除"按钮
2. 弹出"modal", 是否确认删除
3. 点击"确认"按钮
4. 删除Flow

### Flow Schedule -- 完成
1. 从 db 获取所有 flowDefinitionEntity definition
2. 解析 cron 表达式
3. 提交 cron 调度任务
   1. 判断当前是否有 Flow 正在进行. 是则 continue, 否则 -> Flow 执行

### Flow执行 -- 完成
1. 获得 "Flow Definition"
2. 解析 Nodes, 对 Nodes 排序, 获得 List<Nodes> 有序列表. 
3. by 每个 node, 取出 inputsParam, 放入 context, 执行 node
   1. 每个 flow 执行前, 新建 context, mq 发出 "flow 执行"
   2. 每个 node 执行前, 取出 inputsParam, 放入 context, mq 发出 "node 执行"
   3. 每个 node 执行后
      1. 正常结束, 则取出 outputsParam, 放入 context, mq 发出 "node 成功"
      2. 异常结束, mq 发出 "node 失败" 和 "flow 失败", 终止 flow
   4. flow 结束后, 发出 "flow 成功"

## 功能用户故事
### RESTIC 备份 -- 完成
1. 定义一个 backup 节点
2. 接收 source_directory, backup_repository 和 backup_password 三个参数
3. 成功则输出/聚合 json, 存储在 context_data 中
### RESTIC 备份信息表
1. 定义一个 json extract 节点
2. 从 flow execution 的 context data 中增量获取 json result
3. mapping 到数据库表中(动态建表?)
### 云盘备份
1. 定义一个 云盘备份节点
2. 从 restic 备份表中获取未上传的备份信息
3. 上传
### RCLONE 文件夹同步
1. 定义一个 copy 节点
2. 接收 source_directory, dest_directory 两个参数
3. 成功则输出/聚合 json, 存储在 context data 中


## DAG 引擎用户故事
### NODE 执行日志