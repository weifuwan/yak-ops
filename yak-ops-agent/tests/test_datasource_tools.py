"""Data source catalog tools used to build sync-task parameters."""

from __future__ import annotations

import asyncio
import unittest
from unittest.mock import patch

from yak_agent.config import Settings
from yak_agent.tools import runtime
from yak_agent.tools.builder import build_tools
from yak_agent.yak_client import YakAuth

_DATASOURCE_TOOLS = {
    "list_data_sources",
    "get_data_source",
    "list_data_source_tables",
    "list_data_source_columns",
}


def _settings() -> Settings:
    return Settings(
        llm_base_url="http://llm.example",
        llm_api_key="test-key",
        llm_model="test-model",
        yak_ops_base_url="http://yak.example",
        yak_ops_token="test-token",
    )


class DataSourceToolTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tools = {
            tool.name: tool
            for tool in build_tools(
                _settings(),
                auth=YakAuth(base_url="http://yak.example", token="test-token"),
                project_code=1,
            )
        }

    def _call(self, name: str, **kwargs):
        with patch.object(runtime, "yak_get", return_value={"totalList": []}) as get:
            asyncio.run(self.tools[name](**kwargs))
        get.assert_called_once()
        _auth, path, query = get.call_args.args
        return path, query

    def test_tools_are_registered_read_only(self) -> None:
        for name in _DATASOURCE_TOOLS:
            with self.subTest(name=name):
                tool = self.tools[name]
                self.assertTrue(tool.is_read_only)
                self.assertTrue(tool.is_concurrency_safe)

    def test_list_data_sources_maps_filters_and_paging(self) -> None:
        path, query = self._call("list_data_sources", keyword="orders", db_type="MYSQL")

        self.assertEqual(path, "/projects/1/datasources")
        self.assertEqual(
            query,
            {"searchVal": "orders", "type": "MYSQL", "pageNo": 1, "pageSize": 20},
        )

    def test_get_data_source_uses_path_id(self) -> None:
        path, query = self._call("get_data_source", data_source_id=7)

        self.assertEqual(path, "/projects/1/datasources/7")
        self.assertIsNone(query)

    def test_list_tables_maps_catalog_query(self) -> None:
        path, query = self._call(
            "list_data_source_tables",
            data_source_id=7,
            database="ds",
            keyword="orders",
        )

        self.assertEqual(path, "/projects/1/datasources/tables")
        self.assertEqual(
            query,
            {
                "datasourceId": 7,
                "database": "ds",
                "keyword": "orders",
                "pageNo": 1,
                "pageSize": 50,
            },
        )

    def test_list_columns_maps_table_query(self) -> None:
        path, query = self._call(
            "list_data_source_columns",
            data_source_id=7,
            database="ds",
            table_name="orders",
        )

        self.assertEqual(path, "/projects/1/datasources/tableColumns")
        self.assertEqual(
            query,
            {"datasourceId": 7, "database": "ds", "tableName": "orders"},
        )

    def test_schemas_require_real_data_source_identity(self) -> None:
        self.assertEqual(
            self.tools["get_data_source"].input_schema["required"],
            ["data_source_id"],
        )
        self.assertEqual(
            set(self.tools["list_data_source_tables"].input_schema["required"]),
            {"data_source_id", "database"},
        )
        self.assertEqual(
            set(self.tools["list_data_source_columns"].input_schema["required"]),
            {"data_source_id", "database", "table_name"},
        )
        self.assertEqual(
            self.tools["list_data_sources"].input_schema["properties"]["page_size"]["default"],
            20,
        )


if __name__ == "__main__":
    unittest.main()
