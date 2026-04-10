# Vuln Demo App — Design Document

## 1. Overview

A deliberately vulnerable Java web application designed as a penetration testing target for **AWS Security Agent**. The app exposes 7 common OWASP vulnerability categories through a simple user-facing interface, enabling security teams to validate automated vulnerability discovery and remediation capabilities.

## 2. Purpose & Scope

- Serve as a controlled pen-test target for AWS Security Agent demonstrations
- Cover OWASP Top 10 vulnerability categories in a single lightweight application
- Run on minimal infrastructure (t2.micro, ~75 MB memory footprint)

**Out of scope:** authentication/authorization flows, production-grade error handling, multi-environment configuration.

## 3. Technology Stack

| Component | Technology | Version |
|---|---|---|
| Language | Java | 17 (Amazon Corretto) |
| Framework | Spring Boot | 3.2.5 |
| Template Engine | Thymeleaf | (managed by Spring Boot) |
| Database | H2 (in-memory) | (managed by Spring Boot) |
| Build Tool | Maven | 3.9+ |
| Packaging | Fat JAR (embedded Tomcat) | — |

## 4. Infrastructure

```
Internet
   │
   ├── ALB (threetier-alb) ──► port 80
   │                              │
   │                              ▼
   └── Public IP (3.112.14.68) ──► EC2 (3tier-web, t2.micro, AL2023)
                                       │
                                       ▼
                                   JVM (Xmx=256m)
                                   Spring Boot on port 80
                                   H2 in-memory DB
```

- Region: ap-northeast-1 (Tokyo)
- Instance: `i-05090bfc6a7302273` (3tier-web)
- Security Group: `sg-0ab0237dba51b0b62` — port 22 (restricted), port 80 (0.0.0.0/0)
- Deployed as systemd service (`vuln-app.service`)

## 5. Data Model

### users

| Column | Type | Description |
|---|---|---|
| id | INT (PK) | User ID |
| username | VARCHAR(255) | Login name |
| password | VARCHAR(255) | Plaintext password (intentional) |
| role | VARCHAR(50) | `admin` or `user` |

Seed data: `admin/admin123`, `user1/password1`, `user2/password2`

### notes

| Column | Type | Description |
|---|---|---|
| id | INT (PK, auto-increment) | Note ID |
| user_id | INT | Owner user ID |
| content | VARCHAR(1000) | Note body |

Seed data: 2 notes (admin secret note, user1 personal note)

## 6. Endpoint Inventory

| # | Method | Path | Response | View |
|---|---|---|---|---|
| 1 | GET | `/` | HTML | index.html (Thymeleaf) |
| 2 | GET | `/search?q=` | HTML | search.html (Thymeleaf) |
| 3 | GET | `/greet?name=` | Raw HTML | inline |
| 4 | GET | `/file?name=` | Plain text | inline |
| 5 | GET | `/ping?host=` | Plain text | inline |
| 6 | GET | `/fetch?url=` | Plain text | inline |
| 7 | GET | `/note/{id}` | Plain text | inline |
| 8 | GET | `/debug` | JSON | @ResponseBody |

Additionally, H2 Console is exposed at `/h2-console` (enabled via config).

## 7. Vulnerability Matrix

| # | OWASP Category | Endpoint | Root Cause | Example Exploit |
|---|---|---|---|---|
| 1 | A03:2021 Injection (SQLi) | `/search` | String concatenation in SQL query | `q=' OR '1'='1` |
| 2 | A03:2021 Injection (XSS) | `/greet` | Unescaped user input in HTML response | `name=<script>alert(1)</script>` |
| 3 | A01:2021 Broken Access Control (Path Traversal) | `/file` | No path validation on file read | `name=../../etc/passwd` |
| 4 | A03:2021 Injection (Command Injection) | `/ping` | User input passed to `Runtime.exec` via shell | `host=;cat /etc/passwd` |
| 5 | A10:2021 SSRF | `/fetch` | No URL allowlist/validation | `url=http://169.254.169.254/latest/meta-data/` |
| 6 | A01:2021 Broken Access Control (IDOR) | `/note/{id}` | No authorization check on resource access | `/note/1` (access admin's note) |
| 7 | A02:2021 Cryptographic Failures / A05:2021 Security Misconfiguration | `/debug` | Hardcoded secrets exposed via endpoint | Direct access to `/debug` |

## 8. Component Diagram

```
┌─────────────────────────────────────────────────┐
│                  Spring Boot App                 │
│                                                  │
│  ┌──────────┐    ┌──────────────────────────┐   │
│  │   App    │    │     VulnController        │   │
│  │ (main)   │    │                           │   │
│  └──────────┘    │  /           → index.html │   │
│                  │  /search     → search.html│   │
│                  │  /greet      → raw HTML   │   │
│                  │  /file       → filesystem  │   │
│                  │  /ping       → OS shell   │   │
│                  │  /fetch      → HTTP client│   │
│                  │  /note/{id}  → H2 DB      │   │
│                  │  /debug      → JSON       │   │
│                  └──────────┬───────────────┘   │
│                             │                    │
│                  ┌──────────▼───────────────┐   │
│                  │   H2 In-Memory Database   │   │
│                  │   tables: users, notes    │   │
│                  └──────────────────────────┘   │
│                                                  │
│  ┌──────────────────────────────────────────┐   │
│  │         Embedded Tomcat (port 80)         │   │
│  └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

## 9. Build & Deploy

```bash
# Build
cd /tmp/vuln-app
mvn package -DskipTests

# Deploy to EC2 (via EC2 Instance Connect)
aws ec2-instance-connect send-ssh-public-key \
  --instance-id i-05090bfc6a7302273 \
  --instance-os-user ec2-user \
  --ssh-public-key file:///tmp/deploy_key.pub \
  --profile test --region ap-northeast-1

scp -i /tmp/deploy_key target/vuln-app-1.0.jar ec2-user@3.112.14.68:~
ssh -i /tmp/deploy_key ec2-user@3.112.14.68 "sudo systemctl restart vuln-app"
```

## 10. Configuration

`application.properties`:

| Property | Value | Notes |
|---|---|---|
| `server.port` | 80 | Requires root or `CAP_NET_BIND_SERVICE` |
| `spring.datasource.url` | `jdbc:h2:mem:vulndb` | In-memory, data lost on restart |
| `spring.h2.console.enabled` | true | Intentional — additional attack surface |
| `spring.h2.console.settings.web-allow-others` | true | Allows remote H2 console access |

## 11. Security Considerations

> ⚠️ This application is **intentionally vulnerable**. It must NEVER be deployed in a production environment or exposed to untrusted networks beyond controlled demo scenarios.

Post-demo cleanup checklist:
1. Remove `0.0.0.0/0` from security group port 80
2. Stop the `vuln-app` systemd service
3. Re-enable nginx if needed for the original 3-tier demo
4. Consider stopping or terminating the EC2 instance
