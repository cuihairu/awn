export default defineAppConfig({
  pages: [
    'pages/index/index',
    'pages/event/index',
    'pages/session/index',
    'pages/order-confirm/index',
    'pages/order-pay/index',
    'pages/campaign/index',
  ],
  window: {
    backgroundTextStyle: 'light',
    navigationBarBackgroundColor: '#ffffff',
    navigationBarTitleText: '麦芒网',
    navigationBarTextStyle: 'black',
  },
  tabBar: {
    color: '#78716c',
    selectedColor: '#a16207',
    list: [{ pagePath: 'pages/index/index', text: '首页' }],
  },
})
