'use strict';

// 上游 latest-version@5.1.0 → package-json@6.5.0 → got@9.6.0 链带无补丁
// advisory（got 需 ≥11.8.5，而 got 11 起纯 ESM 无法以 CJS require 落地）。
// 该链在本仓的唯一消费点是 `taro update` 交互式升级命令（update.js:14 顶层
// require、处理器内才调用），本仓升级一律显式 pnpm install，从不运行它。
// 以 stub 替换：加载无害（CLI 启动注册命令时 require 也不炸），调用即报错。
module.exports = function latestVersion() {
  throw new Error(
    'latest-version is stubbed out in this repo (vulnerable got@9 chain removed, see todo.md item 315). ' +
      '`taro update` is not supported; run explicit pnpm installs to upgrade dependencies.'
  );
};
