export default defineAppConfig({
  pages: ['pages/index/index'],
  window: {
    backgroundTextStyle: 'light',
    navigationBarBackgroundColor: '#ffffff',
    navigationBarTitleText: '麦芒网',
    navigationBarTextStyle: 'black',
  },
  tabBar: {
    color: '#78716c',
    selectedColor: '#a16207',
    list: [
      { pagePath: 'pages/index/index', text: '首页' },
    ],
  },
})
