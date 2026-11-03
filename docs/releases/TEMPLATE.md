# Yak Ops vX.Y.Z

**Released:** YYYY-MM-DD

## Overview

一句话说明这个版本解决的核心问题。描述用户可感知的变化，不写研发工作量。

## Highlights

- **主题一：** 之前的问题、现在的行为、用户获得的收益。
- **主题二：** 同上。重点通常不超过五项。

## New Features

新功能、适用范围、入口及关键使用方式。没有则写 `None`。

## Improvements

性能、可靠性、可观测性或交互改善；用可验证的事实说明。没有则写 `None`。

## Bug Fixes

按用户影响归纳重要修复，关联 PR。没有单独记录则明确写 `None`，不要编造。

## Breaking Changes

必须说明不兼容的 API、配置、部署、数据库或状态变更及受影响版本；若没有，写 `None`。

## Compatibility

明确 JDK / 数据库 / Source / Sink / 运行环境及已验证范围；不要推定所有数据库版本都兼容。

## Upgrade Guide

列出从哪些已发布版本能升级、需要的备份与操作、迁移或回滚限制。仅新装时必须写清楚。

## Known Limitations

已知未支持的功能、行为边界、重要风险；没有则写 `None`。

## Downloads

列出版本化分发包、SHA256SUMS、Docker 镜像和正式 Release 链接，不使用 latest 作为版本标识。

## References

与本版本相关的 GitHub Release、Compare、重要 PR、设计讨论和 Release Gate 证据；不粘贴自动生成的完整 PR 清单。

<!--
复制成 docs/releases/vX.Y.Z.md 后，将占位内容替换成经过验证的事实。
上面的栏目名是稳定契约，脚本会验证所有正式版本均有非空内容。
历史版本不得按后来 main 的实现重新描述。发布后只能添加注明日期的 Errata。
-->
