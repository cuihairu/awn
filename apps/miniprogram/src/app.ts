import { PropsWithChildren } from 'react'
import { useLaunch } from '@tarojs/taro'

import './app.css'

function App({ children }: PropsWithChildren) {
  useLaunch(() => {
    console.log('AWN miniprogram launched.')
  })

  return children
}

export default App
