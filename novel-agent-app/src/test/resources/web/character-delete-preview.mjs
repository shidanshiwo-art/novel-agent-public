// 独立 UI 验证服务器：npm 依赖来自 novel-agent-web，所有 API 数据仅保存在内存中。
// 启动：node novel-agent-app/src/test/resources/web/character-delete-preview.mjs
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('../../../../../novel-agent-web/', import.meta.url))
const require = createRequire(path.join(root, 'package.json'))
const { createServer } = await import(require.resolve('vite'))
const project = { projectCode: 'delete-ui-preview', title: '角色删除界面测试', genre: '玄幻', targetChapterCount: 10, wordsPerChapter: 2000, currentChapterNumber: 0, status: 'DRAFT' }
let characters = [
  { characterCode: 'preview-lin', name: '林澈', roleType: 'MALE_LEAD', gender: 'MALE', personality: '冷静谨慎', backgroundStory: '仅用于界面验证的内存数据。', lifeStatus: 'ALIVE', status: 'ACTIVE' },
  { characterCode: 'preview-shen', name: '沈月', roleType: 'SUPPORTING', gender: 'FEMALE', personality: '果断', backgroundStory: '用于验证删除时其他人物保留。', lifeStatus: 'ALIVE', status: 'ACTIVE' },
]

const server = await createServer({
  root,
  server: { host: '127.0.0.1', port: 5175, strictPort: true, proxy: {} },
  plugins: [{
    name: 'character-deletion-preview-fixtures',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (!req.url?.startsWith('/api/')) return next()
        const base = '/api/v1/novels/projects/delete-ui-preview'
        let data
        if (req.method === 'GET' && req.url === base) data = project
        else if (req.method === 'POST' && req.url === `${base}/chapters/list`) data = []
        else if (req.method === 'GET' && req.url === `${base}/bible`) data = null
        else if (req.method === 'GET' && req.url === `${base}/characters`) data = characters
        else if (req.method === 'DELETE' && req.url === `${base}/characters/preview-shen`) {
          res.statusCode = 409
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ code: '0002', info: '角色已被正式人物状态事实引用，无法删除；请先处理相关引用', data: null }))
          console.log('测试结果：模拟删除失败，未修改内存角色')
          return
        } else if (req.method === 'DELETE' && req.url === `${base}/characters/preview-lin`) {
          characters = characters.filter((character) => character.characterCode !== 'preview-lin')
          data = null
          console.log('测试结果：收到正式 DELETE 路径，仅删除内存角色林澈，沈月保留')
        } else {
          res.statusCode = 404
          res.end('Preview API not found')
          return
        }
        console.log(`测试请求：${req.method} ${req.url}`)
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.end(JSON.stringify({ code: '0000', info: '成功', data }))
      })
    },
  }],
})
await server.listen()
console.log('测试预览 http://127.0.0.1:5175，加载项目代号 delete-ui-preview；无数据库、无真实模型调用')
