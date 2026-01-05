# KeFu Admin Vue (v2)

炫酷高端的客服管理后台（Vue 3 + Vite + Tailwind）。

## 结构
- 根目录有 `package.json`（确保能 `pnpm i` 或 `npm i`）
- 使用 `.env` 配置后端地址（已附 `.env.example`）

## 使用
```bash
# 解压并进入目录
cd kefu-admin-vue

# 安装依赖
pnpm i        # 或 npm i / yarn

# 本地开发
pnpm dev      # http://localhost:5173
```

可选：复制 `.env.example` 为 `.env` 并按需编辑：
```
VITE_API_BASE=https://java.ise-app.xyz
```

登录页默认填了演示账号（root / P@ssw0rd!）。
