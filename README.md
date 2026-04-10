# Vuln Demo App

故意包含常见安全漏洞的 Java Web 应用，用于 [AWS Security Agent](https://aws.amazon.com/security-agent/) 渗透测试演示。

## 漏洞清单

| 端点 | 漏洞类型 | OWASP |
|---|---|---|
| `/search?q=` | SQL Injection | A03:2021 |
| `/greet?name=` | Reflected XSS | A03:2021 |
| `/ping?host=` | Command Injection | A03:2021 |
| `/file?name=` | Path Traversal | A01:2021 |
| `/fetch?url=` | SSRF | A10:2021 |
| `/note/{id}` | IDOR | A01:2021 |
| `/debug` | Sensitive Data Exposure | A02:2021 |
| `/h2-console` | Security Misconfiguration | A05:2021 |

## 技术栈

Java 17 / Spring Boot 3.2.5 / H2 (in-memory) / Thymeleaf

## 快速开始

```bash
mvn package -DskipTests
java -Xmx256m -jar target/vuln-app-1.0.jar
# 访问 http://localhost/
```

## 文档

- [DESIGN.md](DESIGN.md) — 架构设计文档
- [openapi.yaml](openapi.yaml) — OpenAPI 3.0 规范

## ⚠️ 警告

本应用**故意包含安全漏洞**，仅用于受控环境下的安全测试演示，严禁部署到生产环境。
