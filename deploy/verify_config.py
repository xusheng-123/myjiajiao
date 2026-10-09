#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
润物细无声 · NURTURE — Release 配置安全断言（P1-1 / P0-4 / 安全验收 4、5 的配置侧）
用法：python deploy/verify_config.py   （在 deploy/ 的上级目录运行，或任意位置给绝对路径）
断言失败即退出码 1 —— 部署流水线可作为门禁调用。
"""
import re
import sys
from pathlib import Path

DEPLOY = Path(__file__).resolve().parent
FAILED = []


def check(name, cond, detail=""):
    print(("  [OK]   " if cond else "  [FAIL] ") + name + ("" if cond else f" -> {detail}"))
    if not cond:
        FAILED.append(name)


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


print("== 编排文件断言 ==")
base = read(DEPLOY / "docker-compose.yml")
prod = read(DEPLOY / "docker-compose.prod.yml")
dev = read(DEPLOY / "docker-compose.dev.yml")

check("postgres 未映射宿主机端口（5432 仅容器网）",
      re.search(r"postgres:\n(?:.+\n)*?    ports: !override \[\]", prod) is not None
      and "5432:5432" not in base.replace('127.0.0.1:5432:5432', '')
      or ('ports: !override []' in prod))
check("backend 生产层移除 8080 宿主机发布（P1-1）", "ports: !override []" in prod.split("backend:")[1][:200])
check("backend healthcheck 存在且指向 /actuator/health（P0-2）",
      "actuator/health" in base and "healthcheck" in base.split("backend:")[1][:2000])
check("cloudflared 依赖 backend service_healthy（P0-2）",
      "service_healthy" in base[base.find("cloudflared:"):base.find("cloudflared:") + 800])
check("Tunnel token 仅经环境变量注入（P0-4）",
      "${CLOUDFLARE_TUNNEL_TOKEN}" in base and "eyJ" not in base.replace("${CLOUDFLARE_TUNNEL_TOKEN}", ""))
check("全服务 restart: unless-stopped（P1-5）", base.count("restart: unless-stopped") >= 3)
check("Docker 日志轮转 max-size/max-file（P1-6）", 'max-size: "10m"' in base and 'max-file: "3"' in base)
check("生产层强制关闭支付沙箱（P0-3）", 'NURTURE_PAYMENT_SANDBOX_ENABLED: "false"' in prod)
check("生产层强制关闭契约匿名访问", 'NURTURE_SECURITY_APIDOCSPUBLIC: "false"' in prod)
check("生产层 include-message=never（P1-9）", 'NURTURE_ERROR_INCLUDE_MESSAGE: "never"' in prod)
check("生产层 CORS 域名必填注入（P1-2）", "NURTURE_CORS_ALLOWED_ORIGINS:${NURTURE_CORS_ALLOWED_ORIGINS:?" in prod.replace(" ", ""))
check("dev 覆盖层仅本地回环映射 5432", "127.0.0.1:5432:5432" in dev)

print("== 应用配置断言 ==")
app_yaml = read(DEPLOY.parent / "backend" / "src" / "main" / "resources" / "application.yml")
check("CORS 允许源环境变量化（P1-2）", "NURTURE_CORS_ALLOWED_ORIGINS" in app_yaml)
check("配置中无占位域名 nurture.example", "nurture.example" not in app_yaml)
check("include-message 经环境变量控制（P1-9）", "NURTURE_ERROR_INCLUDE_MESSAGE" in app_yaml)
check("actuator 仅暴露 health,info（P0-2）", "include: health,info" in app_yaml)
check("health show-details=never", "show-details: never" in app_yaml)
check("探针启用（liveness/readiness）", "probes:" in app_yaml and "enabled: true" in app_yaml)

sec = read(DEPLOY.parent / "backend" / "src" / "main" / "java" / "com" / "nurture" / "shared" / "security" / "SecurityConfig.java")
check("SecurityConfig 不含硬编码占位域名", "nurture.example" not in sec)
check("CORS 读取注入清单", "corsAllowedOrigins" in sec)

print("== 密钥纪律断言 ==")
env_prod = read(DEPLOY / "env.production.example")
env_stg = read(DEPLOY / "env.staging.example")
check("env 模板不含真实 token（eyJ 开头占位除外）", "eyJ..." in env_prod and len(re.findall(r"eyJ[A-Za-z0-9_-]{20,}", env_prod)) == 0)
check("staging 模板声明沙箱开启", "NURTURE_PAYMENT_SANDBOX_ENABLED=true" in env_stg)
check("production 模板声明沙箱关闭", "NURTURE_PAYMENT_SANDBOX_ENABLED=false" in env_prod)

print("== 日志纪律断言（P1-3 源码级）==")
sms_store = read(DEPLOY.parent / "backend" / "src" / "main" / "java" / "com" / "nurture" / "identity" / "app" / "SmsCodeStore.java")
check("SmsCodeStore 不再输出验证码明文", "验证码：{}" not in sms_store and "maskPhone" in sms_store)

print("== Angular 双主机名断言（STAGING 收口）==")
fe = DEPLOY.parent / "frontend"
pkg = (fe / "package.json")
import json as _json
pkgj = _json.loads(pkg.read_text(encoding="utf-8"))
check("npm build:staging 脚本存在", "build:staging" in pkgj.get("scripts", {}))
check("npm build:pages 脚本存在", "build:pages" in pkgj.get("scripts", {}))
ang = _json.loads((fe / "angular.json").read_text(encoding="utf-8"))
fr = ang["projects"]["nurture-frontend"]["architect"]["build"]["configurations"]["production"].get("fileReplacements", [])
check("production fileReplacements 指向 environment.production.ts",
      any("environment.production.ts" in f.get("with", "") for f in fr))
check("write-env.mjs 构建变量注入脚本存在", (fe / "scripts" / "write-env.mjs").exists())
check("write-env 读取 NURTURE_API_HOST", "NURTURE_API_HOST" in (fe / "scripts" / "write-env.mjs").read_text(encoding="utf-8"))
check("Pages SPA 回退 _redirects 存在", (fe / "public" / "_redirects").exists()
      and "index.html" in (fe / "public" / "_redirects").read_text(encoding="utf-8"))
import re as _re
def _api_base(txt):
    m = _re.search(r"apiBaseUrl:\s*'([^']*)'", txt)
    return m.group(1) if m else None
dev_base = _api_base((fe / "src" / "environments" / "environment.ts").read_text(encoding="utf-8"))
check("dev 环境文件 apiBaseUrl 为同源相对路径（值非 localhost）", dev_base == "/api")
prod_base = _api_base((fe / "src" / "environments" / "environment.production.ts").read_text(encoding="utf-8"))
check("仓库内 prod 环境文件无硬编码域名（回退值，域名仅由构建注入）", prod_base == "/api")

env_stg_txt = read(DEPLOY / "env.staging.example")
env_prod_txt = read(DEPLOY / "env.production.example")
check("staging 模板声明 api-staging 主机名", "NURTURE_API_HOST=api-staging.<域名>" in env_stg_txt)
check("staging 模板 CORS 精确源 www-staging", "https://www-staging.<域名>" in env_stg_txt)
check("staging 模板沙箱开启（隔离纪律）", "NURTURE_PAYMENT_SANDBOX_ENABLED=true" in env_stg_txt)
check("production 模板声明 api 主机名", "NURTURE_API_HOST=api.<域名>" in env_prod_txt)
check("production 模板 CORS 精确源 www", "https://www.<域名>" in env_prod_txt)
check("production 模板沙箱关闭（隔离纪律）", "NURTURE_PAYMENT_SANDBOX_ENABLED=false" in env_prod_txt)
check("staging 与 production 模板不混用（prod 无 staging 主机名）",
      "api-staging" not in env_prod_txt and "www-staging" not in env_prod_txt)
check("env 模板无禁用域名（nurture.example / localhost / 127.0.0.1）",
      all(x not in env_stg_txt + env_prod_txt for x in ["nurture.example", "localhost", "127.0.0.1"]))
check("env 模板含 NURTURE_API_HOST 构建变量说明", "NURTURE_API_HOST" in env_stg_txt and "NURTURE_API_HOST" in env_prod_txt)

check("Staging 专用覆盖层存在且沙箱置 true",
      (DEPLOY / "docker-compose.staging.yml").exists()
      and 'NURTURE_PAYMENT_SANDBOX_ENABLED: "true"' in (DEPLOY / "docker-compose.staging.yml").read_text(encoding="utf-8"))

print()
if FAILED:
    print(f"未通过 {len(FAILED)} 项：{FAILED}")
    sys.exit(1)
print("全部配置安全断言通过。")
