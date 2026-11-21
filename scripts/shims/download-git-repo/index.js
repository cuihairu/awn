'use strict';

// 上游 download-git-repo@3.0.2 已停止维护，其传递链携带多个无补丁漏洞
// （git-clone 命令注入 / decompress 原型污染 / got 8）。整条链只服务于
// `taro init` 的远程模板拉取，本仓库模板全部在仓内，从不调用该功能。
// 以 stub 替换：加载无害，调用即报错并给出明确指引。
module.exports = function downloadGitRepo() {
  throw new Error(
    'download-git-repo is stubbed out in this repo (vulnerable chain removed, see todo.md VULN-AWN-001). ' +
      '`taro init` remote template download is not supported; templates live in-repo.'
  );
};
