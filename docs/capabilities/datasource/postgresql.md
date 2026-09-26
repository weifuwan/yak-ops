# PostgreSQL Datasource

Status: Active

Depends On:
- [Datasource Domain](./README.md)
- `yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN_RULES.md`
- `yak-ops-ui/apps/web/app/datasource/DATASOURCE_RULES.md`

## Product Contract

PostgreSQL uses Database as the Datasource connection target.

```text
PostgreSQL Server
└── Database
    ├── public
    ├── ods
    └── app
```

Datasource Create / Edit only owns:

```text
host
port
database
username
password
properties
```

Schema is not a top-level connection field. The UI must not add a dedicated Schema control or write `public` implicitly.

The JDBC preview is:

```text
jdbc:postgresql://host:port/database
```

When a default PostgreSQL search path is required, use the provider-owned advanced property:

```text
currentSchema = public
```

Schema discovery and explicit `schema.table` qualification remain Catalog responsibilities.

## Runtime Flow

```text
Datasource Form
→ service/datasource
→ POST /api/v1/data-source/connect-test-with-param
→ DataSourceServiceImpl
→ DataSourcePluginRegistry
→ PostgreSqlDataSourcePlugin
→ PostgreSQL JDBC Driver
→ DriverManager.getConnection(...)
→ Connection.isValid(timeout)
```

Create and Update reuse the same structured `connectionParams` Contract as unsaved Connection Test.

## UI Acceptance

The PostgreSQL frontend flow must satisfy all of the following:

- Create Wizard exposes PostgreSQL as a relational datasource.
- Selecting PostgreSQL resets the default port to `5432`.
- JDBC Preview uses the PostgreSQL scheme and Database path.
- Create / Edit do not render a Schema field.
- Advanced properties stay generic Key / Value inputs.
- `POSTGRESQL` / `POSTGRES` compatibility aliases normalize to `POSTGRE_SQL` inside form state.
- The list renders the product label `PostgreSQL`, never the internal canonical text `POSTGRE_SQL`.
- Edit restores Host / Port / Database / Username / Password mask / Properties from detail data.
- Connection Test submits the current unsaved form values instead of testing the last persisted connection.

## End-to-End Acceptance Matrix

| Scenario | Expected |
| --- | --- |
| PostgreSQL selected | Port defaults to `5432` |
| Host / port / database edited | JDBC Preview updates immediately |
| Valid PostgreSQL connection | Connection Test returns success |
| Invalid host | Connection Test fails |
| Invalid port | Connection Test fails |
| Missing database | Frontend validation blocks request |
| Unknown database | Real PostgreSQL connection fails |
| Invalid username / password | Real PostgreSQL connection fails |
| No Schema configured | Datasource can connect to the Database |
| `properties.currentSchema=public` | Property reaches PostgreSQL Driver without creating a top-level Schema field |
| Create then Edit | Structured connection fields round-trip correctly |
| Saved datasource batch test | Uses persisted PostgreSQL connection and updates connection status |

## Acceptance Evidence

Code-path acceptance is enforced by the current implementation:

- frontend PostgreSQL type, default port, URL parse and preview live in `app/datasource/constants.ts` and `form.tsx`.
- frontend Create / Update / Connection Test share `DataSourceConnectionParams`.
- Business routes the explicit `dbType` through `DataSourcePluginRegistry`.
- PostgreSQL Provider owns Driver, URL, aliases and property normalization.
- shared JDBC Connection Test opens a physical connection and calls `Connection.isValid(timeoutSeconds)`.

GitHub Quality Check is the mechanical acceptance gate for formatting, lint, TypeScript, architecture, frontend build, backend compile and backend verify.

A release environment still needs one real PostgreSQL endpoint to execute the positive/negative network rows in the matrix; repository code must not fake those results.
