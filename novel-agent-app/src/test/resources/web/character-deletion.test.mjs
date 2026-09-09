import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'

const webRoot = new URL('../../../../../novel-agent-web/', import.meta.url)
const require = createRequire(new URL('package.json', webRoot))
const ts = require('typescript')
const view = readFileSync(new URL('src/views/SetupView.vue', webRoot), 'utf8')
const script = view.split('<script setup lang="ts">')[1].split('</script>')[0]
const source = ts.createSourceFile('SetupView.ts', script, ts.ScriptTarget.Latest, true)
const removal = source.statements.find((node) => ts.isFunctionDeclaration(node) && node.name?.text === 'removeCharacter')
assert.ok(removal, '角色删除处理函数必须存在')

function fixture() {
  const events = []
  const state = {
    projectStore: { active: { projectCode: 'project-a' } },
    editingCharacterCode: { value: 'char-a' },
    characterCreatorOpen: { value: true },
    characters: { value: [{ characterCode: 'char-a', name: '林澈' }] },
    deletingChar: { value: false }, savingChar: { value: false },
    ElMessageBox: { confirm: async () => { events.push('confirm') } },
    apiDeleteCharacter: async (project, character) => { events.push(`DELETE:${project}/${character}`) },
    loadCharacters: async () => {
      assert.equal(state.characterCreatorOpen.value, false, '刷新前关闭详情')
      assert.equal(state.editingCharacterCode.value, null, '刷新前清空选择')
      assert.equal(state.characters.value.length, 1, '不能手动提前移除本地人物')
      state.characters.value = []
      events.push('refresh')
    },
    ElMessage: { success: () => { events.push('success') } },
  }
  return { state, events, remove: vm.runInNewContext(`(${removal.getText(source)})`, state) }
}

test('成功删除：请求后关闭、清空选择、刷新、再提示', async () => {
  const f = fixture()
  await f.remove()
  assert.deepEqual(f.events, ['confirm', 'DELETE:project-a/char-a', 'refresh', 'success'])
  assert.equal(f.state.characters.value.length, 0)
  assert.equal(f.state.deletingChar.value, false)
  console.log('成功时序：', f.events)
})

for (const reason of ['cancel', 'close']) {
  test(`确认框 ${reason} 不发送删除请求`, async () => {
    const f = fixture()
    f.state.ElMessageBox.confirm = async () => { throw reason }
    await f.remove()
    assert.deepEqual(f.events, [])
    assert.equal(f.state.characterCreatorOpen.value, true)
    assert.equal(f.state.characters.value.length, 1)
    assert.equal(f.state.deletingChar.value, false)
    console.log(`${reason}：列表及详情保留，没有 DELETE`)
  })
}

test('后端拒绝删除时保留本地角色、选择和详情', async () => {
  const f = fixture()
  f.state.apiDeleteCharacter = async () => { throw new Error('人物已被引用，无法删除') }
  await f.remove()
  assert.deepEqual(f.events, ['confirm'])
  assert.equal(f.state.characters.value.length, 1)
  assert.equal(f.state.editingCharacterCode.value, 'char-a')
  assert.equal(f.state.characterCreatorOpen.value, true)
  assert.equal(f.state.deletingChar.value, false)
  console.log('后端拒绝：角色、选择、详情保留，无刷新或成功提示')
})

test('删除请求尚未完成时不移除角色，重复点击不重复请求', async () => {
  const f = fixture()
  let complete
  let calls = 0
  f.state.apiDeleteCharacter = () => {
    calls++
    return new Promise((resolve) => { complete = resolve })
  }
  const pending = f.remove()
  await Promise.resolve()
  await f.remove()
  assert.equal(calls, 1)
  assert.equal(f.state.characters.value.length, 1)
  assert.equal(f.state.characterCreatorOpen.value, true)
  assert.equal(f.state.editingCharacterCode.value, 'char-a')
  complete()
  await pending
  assert.equal(f.state.characters.value.length, 0)
  console.log('请求进行中保留角色，重复点击只触发一次DELETE')
})

test('列表刷新失败时不显示成功提示', async () => {
  const f = fixture()
  f.state.loadCharacters = async () => { throw new Error('刷新失败') }
  await f.remove()
  assert.deepEqual(f.events, ['confirm', 'DELETE:project-a/char-a'])
  assert.equal(f.state.characters.value.length, 1)
  assert.equal(f.state.characterCreatorOpen.value, false)
  assert.equal(f.state.editingCharacterCode.value, null)
  console.log('刷新失败：详情已关闭，但不宣告刷新成功')
})

test('确认期间切换项目不发送旧角色删除请求', async () => {
  const f = fixture()
  f.state.ElMessageBox.confirm = async () => { f.state.projectStore.active.projectCode = 'project-b' }
  await f.remove()
  assert.deepEqual(f.events, [])
  console.log('切换项目：取消旧目标删除')
})

function loadTs(relativePath, modules) {
  const filename = fileURLToPath(new URL(relativePath, webRoot))
  const code = ts.transpileModule(readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: false },
  }).outputText
  const exports = {}
  vm.runInNewContext(code, { exports, require: (name) => {
    assert.ok(name in modules, `意外依赖：${name}`)
    return modules[name]
  } }, { filename })
  return exports
}

test('API实际执行DELETE并携带项目和人物编码', async () => {
  let request
  const api = loadTs('src/api/project.ts', { './http': { default: { delete: async (url) => { request = url } } } })
  await api.deleteCharacter('project-a', 'char-a')
  assert.equal(request, '/v1/novels/projects/project-a/characters/char-a')
  console.log('实际调用 http.delete：', request)
})

test('HTTP失败响应显示后端info而不是泛化网络错误', async () => {
  let rejectResponse
  let displayed
  const client = { interceptors: { response: { use: (_, reject) => { rejectResponse = reject } } } }
  loadTs('src/api/http.ts', {
    axios: { default: { create: () => client } },
    'element-plus': { ElMessage: { error: (message) => { displayed = message } } },
  })
  const error = { message: 'Request failed with status code 409', response: { data: { info: '角色已被正式人物状态事实引用，无法删除' } } }
  await assert.rejects(rejectResponse(error), (actual) => actual === error)
  assert.equal(displayed, error.response.data.info)
  console.log('前端展示后端拒绝原因：', displayed)
})
