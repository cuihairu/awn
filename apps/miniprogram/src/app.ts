import { PropsWithChildren } from 'react'
import { useLaunch } from '@tarojs/taro'
import { setTransport } from './lib/mp-api'
import { taroTransport } from './lib/taro-transport'

import './app.css'

function App({ children }: PropsWithChildren) {
  useLaunch(() => {
    // 全端唯一请求通道：Taro.request 适配（API 域名见 lib/taro-transport）
    setTransport(taroTransport)
  })

  return children
}

export default App
