# 一页架构草图

```mermaid
flowchart TD
    U[学习者使用本地单进程应用] --> APP[fund-application 负责启动与装配]
    APP --> A[fund-guardian 独立完成诊断链路]
    APP --> B[fund-integration 独立完成接入链路]
    B --> K[fund-knowledge 保存人工确认后发布的不可变契约与证据]
    K --> A
    A --> C[fund-common 仅共享标识与错误类型]
    B --> C
    K --> C
    APP --> D1[D1 显式启用实验入口后发起同步请求]
    D1 --> API[Spring AI DeepSeekApi 序列化请求并交给 HTTP 客户端]
    API --> DS[DeepSeek 云 API 返回模型响应和用量]
    API --> E[拦截器脱敏请求并保存允许落盘的响应证据]
```

上半部分是既定模块职责，D1 只建立空模块并由 app 装配，尚无业务实现。下半部分是 D1 已实现的调用路径，使用环境变量输入 key 与提示；默认启动不调用模型。

契约、本地治理配置、运行基线、历史故障保持分离；文档声明不能覆盖本地配置，模型不能猜测契约值。后续诊断固定证据版本，执行经过规则与审批，且只模拟。PostgreSQL + pgvector 在 M3 才接入，D1 无数据库。
