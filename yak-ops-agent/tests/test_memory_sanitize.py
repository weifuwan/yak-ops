"""Write-back sanitization: whitelist, redaction and truncation."""

from __future__ import annotations

import unittest

from agentscope.message import (
    AssistantMsg,
    Base64Source,
    DataBlock,
    HintBlock,
    Msg,
    ToolCallBlock,
    UserMsg,
)

from yak_agent.memory.sanitize import (
    MAX_MESSAGE_CHARS,
    MAX_TURN_CHARS,
    redact,
    sanitize_turn,
)

_MEMORY_NAME = "memory"


def _text(msg: Msg) -> str:
    return msg.get_text_content() or ""


class RedactTests(unittest.TestCase):
    def test_secret_assignments_are_redacted(self) -> None:
        text = "password=abc123 token: xyz secret = s3cr3t api_key=K"
        redacted = redact(text)

        self.assertNotIn("abc123", redacted)
        self.assertNotIn("xyz", redacted)
        self.assertNotIn("s3cr3t", redacted)
        self.assertNotIn("K", redacted)
        self.assertIn("<redacted>", redacted)

    def test_bearer_and_url_credentials_are_redacted(self) -> None:
        redacted = redact("Authorization: Bearer abc.def.ghi url=mysql://u:p@host/db")

        self.assertNotIn("abc.def.ghi", redacted)
        self.assertNotIn("u:p@", redacted)

    def test_jdbc_url_is_redacted(self) -> None:
        redacted = redact("jdbc:mysql://user:pass@db:3306/x?useSSL=false")

        self.assertEqual(redacted, "jdbc:<redacted>")

    def test_normal_text_is_untouched(self) -> None:
        text = "任务实例 FAILURE 时先看 Caused by 链"
        self.assertEqual(redact(text), text)


class SanitizeTurnTests(unittest.TestCase):
    def test_keeps_user_text_and_only_the_final_assistant_text(self) -> None:
        messages = [
            UserMsg(name="user", content="我只看 FAILURE 实例"),
            AssistantMsg(name="assistant", content="先查一下"),
            AssistantMsg(name="assistant", content="结论：先看 Caused by"),
            Msg(
                role="assistant",
                name="assistant",
                content=[ToolCallBlock(id="1", name="list_sync_instances", input="{}")],
            ),
        ]

        cleaned = sanitize_turn(messages)

        self.assertEqual(
            [_text(msg) for msg in cleaned], ["我只看 FAILURE 实例", "结论：先看 Caused by"]
        )

    def test_drops_tool_results_hints_and_data_blocks(self) -> None:
        messages = [
            UserMsg(name="user", content="hi"),
            AssistantMsg(name=_MEMORY_NAME, content=[HintBlock(hint="old memory")]),
            Msg(
                role="assistant",
                name="tool",
                content=[{"type": "tool_result", "id": "1", "name": "x", "output": "password=x"}],
            ),
            Msg(
                role="assistant",
                name="assistant",
                content=[
                    DataBlock(source=Base64Source(data="AAAA", media_type="image/png")),
                    {"type": "text", "text": "final"},
                ],
            ),
        ]

        cleaned = sanitize_turn(messages)

        self.assertEqual([_text(msg) for msg in cleaned], ["hi", "final"])

    def test_redacts_and_truncates_message_text(self) -> None:
        long_text = "password=abc " + "x" * 5000
        cleaned = sanitize_turn([UserMsg(name="user", content=long_text)])

        text = _text(cleaned[0])
        self.assertNotIn("abc", text)
        self.assertLessEqual(len(text), MAX_MESSAGE_CHARS + len(" (truncated)"))

    def test_caps_the_whole_turn(self) -> None:
        messages = [
            UserMsg(name="user", content="a" * 8000),
            AssistantMsg(name="assistant", content="b" * 8000),
        ]

        cleaned = sanitize_turn(messages)

        self.assertLessEqual(sum(len(_text(msg)) for msg in cleaned), MAX_TURN_CHARS + 32)

    def test_empty_input(self) -> None:
        self.assertEqual(sanitize_turn([]), [])


if __name__ == "__main__":
    unittest.main()
