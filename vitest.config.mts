/**
 * 根测试配置：只补两件事。
 * 1. .tsx 测试用 React automatic JSX transform（与 Next.js 应用一致），
 *    免得每个组件测试自己 import React。环境默认 node，需要 DOM 的测试在文件头声明。
 * 2. 钉死 TZ=Asia/Shanghai：CI runner 默认 UTC，而业务文案按东八区展示
 *    （如「11 月 6 日 19:30」），不钉死会随 runner 时区漂移。
 */
process.env.TZ = 'Asia/Shanghai'

export default {
  esbuild: {
    jsx: 'automatic',
  },
}
