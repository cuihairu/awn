/**
 * 根测试配置：只补一件事，让 .tsx 测试用 React automatic JSX transform（与 Next.js 应用一致），
 * 免得每个组件测试自己 import React。环境默认 node，需要 DOM 的测试在文件头声明。
 */
export default {
  esbuild: {
    jsx: 'automatic',
  },
}
