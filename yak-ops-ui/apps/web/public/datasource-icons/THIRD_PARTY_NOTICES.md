# Data source icon licenses and provenance

The data source SVGs live in `apps/web/public/datasource-icons` and are served locally by Vite.
The attached HTML snippet contained only `<use href="#icon-...">` symbols, not actual
SVG shapes; this directory deliberately does not bundle Chat2DB's sprite or runtime.

## Copied upstream SVG assets

- `sqlserver.svg` — Microsoft SQL Server icon from [Devicon](https://github.com/devicons/devicon/blob/master/icons/microsoftsqlserver/microsoftsqlserver-original.svg), MIT License.
- `clickhouse.svg` — ClickHouse icon from [Devicon](https://github.com/devicons/devicon/blob/master/icons/clickhouse/clickhouse-original.svg), MIT License.
- `ssh.svg` — SSH icon from [Devicon](https://github.com/devicons/devicon/blob/master/icons/ssh/ssh-original.svg), MIT License.
- `doris.svg` — Apache Doris icon from [Simple Icons](https://github.com/simple-icons/simple-icons/blob/develop/icons/apachedoris.svg), CC0-1.0; recolored for UI visibility.

See `LICENSE.devicon.txt` for the upstream Devicon MIT license notice.

## Independently authored SVG identifiers

- `db2.svg`, `dameng.svg`, `oceanbase.svg`, `starrocks.svg` — simple
  original lettermark-style SVGs authored specifically for Yak Ops.
  They are **not** representations of, or copies of, vendor-provided logo artwork.

Existing MySQL, Oracle and PostgreSQL React SVG components remain unchanged.

Product and company names may be registered trademarks of their respective owners.
The logos identify supported data-source types; no endorsement is implied.
