# Database & Persistence Guide

GoosBoards includes an optional persistence layer for servers that require persistent player scene states, custom variables, or transaction logs across server restarts.

---

## ⚡ Zero Database Footprint by Default

By default, GoosBoards runs with persistence **disabled**:
```yaml
database:
  enabled: false
```
When `enabled: false`, zero database worker threads, zero file handles, and zero SQL connections are initialized. All runtime state is kept in memory.

---

## 🗄️ Supported Storage Engines

When persistence is enabled in `config.yml`, you can select between three database types:

### 1. Embedded H2 Database (Recommended for Single Servers)
* **Configuration**:
  ```yaml
  database:
    enabled: true
    type: H2
  ```
* **Storage Location**: Stored automatically in `plugins/GoosBoards/data.db`.
* **Zero External Setup**: Requires no external database installation or credentials.

---

### 2. MySQL / MariaDB (Recommended for Multi-Server Networks)
* **Configuration**:
  ```yaml
  database:
    enabled: true
    type: MYSQL          # Or MARIADB
    host: "127.0.0.1"
    port: 3306
    database: "goosboards"
    username: "server_user"
    password: "your_secure_password"
    table-prefix: "goosboards_"
    pool:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout-ms: 10000
      idle-timeout-ms: 600000
      max-lifetime-ms: 1800000
  ```

---

## ⚙️ HikariCP Connection Pool Tuning

When using external SQL databases, GoosBoards utilizes [HikariCP](https://github.com/brettwooldridge/HikariCP) for high-performance connection pooling:

* **`maximum-pool-size`** *(default: 10)*: Maximum number of active connections in the pool. For most server loads, 5–10 connections are plenty.
* **`minimum-idle`** *(default: 2)*: Minimum number of idle connections maintained in the pool.
* **`connection-timeout-ms`** *(default: 10000)*: Milliseconds to wait before throwing an exception if all pool connections are in use.
* **`idle-timeout-ms`** *(default: 600000 = 10 min)*: Max time an idle connection is permitted to stay in the pool before retirement.
* **`max-lifetime-ms`** *(default: 1800000 = 30 min)*: Maximum lifespan of a database connection before being cleanly recycled.

---

## 🔄 Automatic Schema Migration

When the database is enabled, GoosBoards automatically generates required tables asynchronously upon server startup using non-blocking DDL statements. Database migrations require no manual SQL scripts to be executed.
