# spring-db-benchmark

Estudo comparativo de estratégias de persistência no Spring Boot com PostgreSQL.

## Estratégias comparadas

| ID | Estratégia | Descrição |
|----|-----------|-----------|
| S1 | `JPA_SAVE_ALL` | `saveAll()` sem otimização — baseline |
| S2 | `JPA_BATCH` | `em.persist()` com flush/clear + `batch_size=500` |
| S3 | `JDBC_BATCH` | `JdbcTemplate.batchUpdate()` com `BatchPreparedStatementSetter` |
| S4 | `NAMED_JDBC_BATCH` | `NamedParameterJdbcTemplate.batchUpdate()` com `SqlParameterSource[]` |
| S5 | `COPY_MANAGER` | `org.postgresql.copy.CopyManager` — máxima performance de ingestão |

## Operações testadas por estratégia

Cada estratégia executa **2 runs** por operação:

- **COLD** — tabela limpa (`TRUNCATE` antes)
- **WARM** — dados já existentes (continua do COLD)

Operações: `INSERT` · `UPDATE` · `DELETE` · `UPSERT` · `SELECT`

## Métricas capturadas

### JVM
- Tempo de execução (ms)
- Delta de memória heap (KB)
- Throughput (registros/segundo)

### PostgreSQL (via `pg_stat_*`)
- WAL gerado (bytes / MB) via `pg_wal_lsn_diff`
- Blocos lidos do disco (`blks_read`)
- Blocos lidos do cache (`blks_hit`)
- Cache hit ratio
- Tuplas afetadas (`n_tup_ins + n_tup_upd + n_tup_del`)
- Checkpoints forçados
- Buffers escritos pelo bgwriter

## Setup

### Pré-requisitos

- Java 17
- Maven
- PostgreSQL 15+ rodando em `localhost:5432`

### Banco de dados

```sql
CREATE DATABASE benchmark_db;
```

### Iniciar a aplicação

```bash
mvn spring-boot:run
```

No startup, o `DataSeeder` insere automaticamente **1 milhão de registros** em `products_staging`
usando `CopyManager` (leva ~5–15s dependendo do hardware). Nas próximas inicializações, o seed é ignorado.

## Uso

### Listar estratégias disponíveis

```bash
GET /benchmark/strategies
```

### Executar benchmark

```bash
# Todas as estratégias, 10.000 registros
POST /benchmark/run

# Volume customizado
POST /benchmark/run?volume=50000

# Estratégia específica
POST /benchmark/run?volume=10000&strategy=S3_JDBC_BATCH
```

### Dashboard (local)

App rodando: abrir `http://localhost:8080/` — dashboard interativo (gráficos, ranking, tabela) com botão para rodar novos benchmarks. Cada run é salvo em `results/benchmark-<timestamp>.json`.

### Publicar resultados no GitHub Pages

Site estático em `docs/` (HTML + Chart.js local + JSONs em `docs/data/`), **sem chamadas de API** — lê tudo de `./data/` via `manifest.json`.

```bash
# copia results/*.json -> docs/data/ e gera docs/data/manifest.json
bash scripts/build-pages.sh

# testar local (simula Pages)
node scripts/serve-docs.js 8090   # abre http://localhost:8090/

# publicar: commitar docs/ e em Settings > Pages apontar para a branch / pasta /docs
```

O dashboard estático carrega automaticamente o run mais recente do manifesto.

### Exemplo de resposta

```json
{
  "results": [
    {
      "strategy": "S3_JDBC_BATCH",
      "operation": "INSERT",
      "volume": 10000,
      "runType": "COLD",
      "executedAt": "2025-01-01T12:00:00Z",
      "elapsedMs": 380,
      "memoryDeltaKb": 3100,
      "throughputPerSecond": 26315.79,
      "walGeneratedBytes": 5242880,
      "walGeneratedMb": 5.0,
      "heapBlocksRead": 12,
      "heapBlocksHit": 1450,
      "cacheHitPercent": "99.18%",
      "tuplesAffected": 10000,
      "checkpointReq": 0,
      "buffersWritten": 0
    }
  ],
  "totalResults": 50,
  "totalBenchmarkMs": 42000
}
```

## Estrutura do projeto

```
src/main/java/com/murilocb/benchmark/
├── BenchmarkApplication.java
├── config/               # (reservado para configurações futuras)
├── controller/
│   └── BenchmarkController.java    # POST /benchmark/run
├── domain/
│   └── Product.java
├── metrics/
│   ├── BenchmarkResult.java        # DTO completo de resultado
│   ├── DatabaseMetricsCollector.java  # captura pg_stat_*
│   ├── DatabaseSnapshot.java
│   └── DatabaseMetricsDelta.java
├── repository/
│   └── ProductJpaRepository.java
├── runner/
│   ├── BenchmarkRunner.java        # executa COLD + WARM por operação
│   └── BenchmarkOrchestrator.java  # carrega dados e distribui para o runner
├── seeder/
│   └── DataSeeder.java             # popula 1M rows no startup
└── strategy/
    ├── BenchmarkStrategy.java      # interface
    ├── JpaSaveAllStrategy.java     # S1
    ├── JpaBatchStrategy.java       # S2
    ├── JdbcBatchStrategy.java      # S3
    ├── NamedJdbcBatchStrategy.java # S4
    └── CopyManagerStrategy.java   # S5
```

## Dicas para resultados mais limpos

- Execute em uma máquina sem outros processos pesados rodando
- Reinicie o PostgreSQL antes de cada sessão completa de benchmark para resetar `pg_stat_*`
- Use `SELECT pg_stat_reset()` para zerar as views antes de um run isolado
- Para WAL mais preciso, configure `wal_level = replica` (padrão no PG 15+)