# 列表查询不发送操作员字段设计

## 背景

WebFE 会为每个 Tuxedo 请求注入服务器配置的 `OPERATOR_NO`。列表服务 `CNAPS4609Q` 和 `CNAPS5702Q` 的 Jolt 元数据将该字段定义为只读输出字段，因此 Jolt 在调用服务前写入 `OPERATOR_NO` 时失败，并由 WebFE 返回 `4002 Tuxedo service call failed: OPERATOR_NO`。

## 设计

在 Jolt 客户端发送请求字段时，根据服务名过滤 `OPERATOR_NO`：

- `CNAPS4609Q` 和 `CNAPS5702Q` 不向 Jolt 写入 `OPERATOR_NO`。
- 其他服务维持现状，继续发送服务器配置的 `OPERATOR_NO`。
- 不修改列表响应字段集合；每条凭证记录中的 `OPERATOR_NO` 仍按现有逻辑读取并映射为 `operatorNo`。
- 不修改 Jolt metadata、Tuxedo C 服务或数据库查询逻辑。

过滤放在 `JoltTuxedoClient` 的请求发送边界，因为只有 Jolt 对元数据读写权限进行约束。保留上游统一生成的请求上下文，可以避免改变 Mock 客户端和其他调用者的现有契约。

## 错误处理

本次变更不调整 `4002` 的异常映射。修复后，列表调用不会再因向只读 `OPERATOR_NO` 写值而进入该异常路径；其他真实的 Jolt 调用异常仍按现有行为返回 `4002`。

## 测试

- 新增 Jolt 客户端测试，证明 `CNAPS4609Q` 和 `CNAPS5702Q` 调用不会写入 `OPERATOR_NO`。
- 验证非列表服务仍会写入 `OPERATOR_NO`，防止审计上下文字段被意外移除。
- 运行 Jolt 客户端及部署元数据相关测试，并运行 WebFE 全量测试。

## 完成标准

带 `workDate`、空 `status`、`includeDeleted=false` 和分页参数的普通列表请求能够越过 Jolt 参数写入阶段；列表响应仍可包含每条记录的操作员号，其他服务的请求字段不变。
