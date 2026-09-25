#!/usr/bin/env python3
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

FORBIDDEN_SNIPPETS = {
    "@Slf4j": "禁止使用 Lombok @Slf4j，统一显式声明 SLF4J Logger",
    "lombok.extern.slf4j.Slf4j": "禁止导入 Lombok @Slf4j",
    "System.out": "禁止使用 System.out 输出应用日志",
    "System.err": "禁止使用 System.err 输出应用日志",
    "printStackTrace(": "禁止直接 printStackTrace",
    "org.apache.logging.log4j": "业务代码不得直接依赖 Log4j2 API",
    "ch.qos.logback": "业务代码不得直接依赖 Logback API",
    "java.util.logging": "业务代码不得直接依赖 java.util.logging",
}

FORBIDDEN_WRAPPER_TYPES = re.compile(
    r"\b(?:class|interface|record|enum)\s+"
    r"(?:LogUtils|LogManager|LoggerManager|LoggerHelper|LoggingHelper|[A-Za-z0-9_]*LoggerFactory)\b"
)

LOGGER_DECLARATION = re.compile(
    r"private\s+static\s+final\s+Logger\s+LOG\s*=\s*"
    r"LoggerFactory\.getLogger\(([A-Za-z0-9_]+)\.class\)\s*;"
)

LOGGER_FIELD = re.compile(
    r"\b(?:private|protected|public)\s+static\s+final\s+Logger\s+([A-Za-z0-9_]+)\b"
)

LOG_CALL_START = re.compile(r"\bLOG\.(trace|debug|info|warn|error)\s*\(")
CHINESE_TEXT = re.compile(r"[\u3400-\u9fff]")
SENSITIVE_LOG_TERM = re.compile(
    r"(?i)\b(?:password|passwd|pwd|authorization|cookie|sessionId|jsessionid|"
    r"secret|connectionJson|connectionParams|originalJson|jdbcUrl|privateKey|"
    r"passphrase|accessKey|secretKey)\b"
)


def production_java_files():
    files = []
    for path in ROOT.rglob("*.java"):
        normalized = path.as_posix()
        if "/src/main/java/" not in normalized:
            continue
        if "yak-ops-ui" in path.parts:
            continue
        files.append(path)
    return sorted(files)


def line_number(text, index):
    return text.count("\n", 0, index) + 1


def extract_log_calls(text):
    calls = []
    for match in LOG_CALL_START.finditer(text):
        depth = 1
        index = match.end()
        in_string = False
        in_char = False
        escaped = False
        while index < len(text) and depth > 0:
            char = text[index]
            if escaped:
                escaped = False
            elif char == "\\" and (in_string or in_char):
                escaped = True
            elif char == '"' and not in_char:
                in_string = not in_string
            elif char == "'" and not in_string:
                in_char = not in_char
            elif not in_string and not in_char:
                if char == "(":
                    depth += 1
                elif char == ")":
                    depth -= 1
            index += 1
        if depth == 0:
            calls.append((match.group(1), match.start(), text[match.start():index]))
    return calls


def first_string_literal(call):
    open_paren = call.find("(")
    if open_paren < 0:
        return None
    index = open_paren + 1
    while index < len(call) and call[index].isspace():
        index += 1
    if index >= len(call) or call[index] != '"':
        return None

    index += 1
    value = []
    escaped = False
    while index < len(call):
        char = call[index]
        if escaped:
            value.append(char)
            escaped = False
        elif char == "\\":
            escaped = True
            value.append(char)
        elif char == '"':
            return "".join(value)
        else:
            value.append(char)
        index += 1
    return None


def verify_file(path):
    text = path.read_text(encoding="utf-8")
    relative = path.relative_to(ROOT)
    violations = []

    for snippet, reason in FORBIDDEN_SNIPPETS.items():
        for match in re.finditer(re.escape(snippet), text):
            violations.append((line_number(text, match.start()), reason))

    wrapper = FORBIDDEN_WRAPPER_TYPES.search(text)
    if wrapper:
        violations.append(
            (
                line_number(text, wrapper.start()),
                f"禁止新增日志包装类型 {wrapper.group(0).split()[-1]}，SLF4J 已是日志抽象边界",
            )
        )

    logger_fields = LOGGER_FIELD.findall(text)
    for field_name in logger_fields:
        if field_name != "LOG":
            field = re.search(
                rf"\b(?:private|protected|public)\s+static\s+final\s+Logger\s+{re.escape(field_name)}\b",
                text,
            )
            violations.append(
                (
                    line_number(text, field.start()) if field else 1,
                    f"Logger 字段必须命名为 LOG，当前为 {field_name}",
                )
            )

    if "LoggerFactory.getLogger(" in text or LOG_CALL_START.search(text):
        declarations = LOGGER_DECLARATION.findall(text)
        expected_type = path.stem
        if len(declarations) != 1:
            violations.append(
                (
                    1,
                    "使用 LoggerFactory 的类必须且只能声明一个 "
                    "private static final Logger LOG = LoggerFactory.getLogger(Xxx.class)",
                )
            )
        elif declarations[0] != expected_type:
            declaration = LOGGER_DECLARATION.search(text)
            violations.append(
                (
                    line_number(text, declaration.start()) if declaration else 1,
                    f"LoggerFactory 必须绑定当前类型 {expected_type}.class",
                )
            )

    for level, start, call in extract_log_calls(text):
        line = line_number(text, start)
        if level == "trace":
            violations.append((line, "Yak Ops V1 禁止 TRACE 日志"))

        message = first_string_literal(call)
        if message is None:
            violations.append((line, "日志首个参数必须是直接字符串字面量，保持事件文本稳定可检索"))
        elif not CHINESE_TEXT.search(message):
            violations.append((line, "仓库自有日志事件描述必须包含中文"))

        if re.search(r'LOG\.(?:debug|info|warn|error)\s*\(\s*String\.format\s*\(', call, re.S):
            violations.append((line, "日志禁止使用 String.format，统一使用 SLF4J {} 占位符"))
        if re.search(r'LOG\.(?:debug|info|warn|error)\s*\(\s*"[^"]*"\s*\+', call, re.S):
            violations.append((line, "日志禁止字符串拼接，统一使用 SLF4J {} 占位符"))

        sensitive = SENSITIVE_LOG_TERM.search(call)
        if sensitive:
            violations.append((line, f"日志调用疑似直接引用敏感字段：{sensitive.group(0)}"))

    return [(relative.as_posix(), line, reason) for line, reason in violations]


def main():
    violations = []
    files = production_java_files()
    for path in files:
        violations.extend(verify_file(path))

    if violations:
        print("Logging rule verification failed:")
        for path, line, reason in violations:
            print(f"- {path}:{line}: {reason}")
        raise SystemExit(1)

    print(f"Logging rule verification passed: checked {len(files)} production Java files.")


if __name__ == "__main__":
    main()
