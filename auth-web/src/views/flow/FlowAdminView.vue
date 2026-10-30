<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import { listDefinitions, deployProcess, listForms, saveForm, definitionXml } from '../../api/flow'

/**
 * Starter diagram. The BPMNDiagram/BPMNPlane block is not optional: bpmn-js
 * renders from DI only, and a purely semantic process fails with
 * "no diagram to display" — which is what made this page look like a canvas
 * that silently refused every node dropped on it.
 */
const STARTER_XML = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="io.github.shiyi.auth.workflow">
  <process id="my-approval" name="我的审批流程" isExecutable="true">
    <startEvent id="start" name="提交申请" flowable:initiator="initiator"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
    <userTask id="approve" name="主管审批" flowable:candidateGroups="ADMIN"/>
    <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
    <endEvent id="end" name="结束"/>
  </process>
  <bpmndi:BPMNDiagram id="Diagram_1">
    <bpmndi:BPMNPlane id="Plane_1" bpmnElement="my-approval">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="182" y="102" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="approve_di" bpmnElement="approve">
        <dc:Bounds x="280" y="80" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="end_di" bpmnElement="end">
        <dc:Bounds x="442" y="102" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="f1_di" bpmnElement="f1">
        <di:waypoint x="218" y="120"/>
        <di:waypoint x="280" y="120"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="f2_di" bpmnElement="f2">
        <di:waypoint x="380" y="120"/>
        <di:waypoint x="442" y="120"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

/**
 * The palette is domain-shaped on purpose: this designer ships approval flows,
 * so it offers the six node kinds an approval actually needs and presets each
 * one to something deployable, instead of the 20+ raw BPMN types where a blank
 * userTask is not assignable and a blank serviceTask throws at runtime.
 */
const NODE_GROUPS = [
  {
    label: '事件',
    items: [
      { key: 'start', label: '开始', desc: '流程起点', type: 'bpmn:StartEvent', tone: 'green', preset: { name: '提交申请' } },
      { key: 'end', label: '结束', desc: '流程终点', type: 'bpmn:EndEvent', tone: 'rose', preset: { name: '结束' } },
    ],
  },
  {
    label: '任务',
    items: [
      {
        key: 'approve', label: '审批', desc: '单人审批 · 按角色分派', type: 'bpmn:UserTask', tone: 'blue',
        preset: { name: '审批', 'flowable:candidateGroups': 'ADMIN' },
      },
      {
        key: 'countersign', label: '会签', desc: '多人并行 · 一票否决', type: 'bpmn:UserTask', tone: 'violet',
        preset: { name: '会签审批', 'flowable:assignee': '${user}' }, countersign: true,
      },
      {
        key: 'ai', label: 'AI 预审', desc: '大模型自动研判', type: 'bpmn:ServiceTask', tone: 'teal',
        preset: { name: 'AI 预审', 'flowable:delegateExpression': '${aiReviewDelegate}' },
      },
    ],
  },
  {
    label: '分支',
    items: [
      { key: 'gateway', label: '条件', desc: '排他网关 · 二选一', type: 'bpmn:ExclusiveGateway', tone: 'amber', preset: { name: '结果判定' } },
    ],
  },
]

const COUNTERSIGN_CONDITION = '${nrOfCompletedInstances >= nrOfInstances || csRejected == true}'

const canvas = ref(null)
const definitions = ref([])
const forms = ref([])
const error = ref('')
const notice = ref('')

const deployName = ref('my-approval')
const xmlText = ref('')
const showXml = ref(false)

const selected = ref(null) // { id, type, name, candidateGroups, countersign }
const formKey = ref('')
const formName = ref('')
const formSchema = ref('')

const drag = ref(null)      // { node, x, y, moved }
const armed = ref(null)     // node picked by a click, waiting for a canvas click
const overCanvas = ref(false)
const fullscreen = ref(false)
let modeler = null
let canvasObserver = null

const hint = computed(() => {
  if (drag.value?.moved) return '松开鼠标放置节点'
  if (armed.value) return `点击画布放置「${armed.value.label}」· Esc 取消`
  return '拖动节点到画布，或点一下节点再点画布'
})

async function load() {
  error.value = ''
  try {
    const [d, f] = await Promise.all([listDefinitions(), listForms()])
    definitions.value = d
    forms.value = f
    if (!formKey.value && d.length) pickForm(d[0].key)
  } catch (e) {
    error.value = e.message
  }
}

function pickForm(key) {
  const f = forms.value.find((x) => x.processKey === key)
  formKey.value = key
  formName.value = f?.formName || ''
  formSchema.value = f?.schemaJson ? pretty(f.schemaJson) : defaultSchema()
}

const pretty = (json) => {
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

const defaultSchema = () =>
  pretty(
    JSON.stringify({
      fields: [
        { key: 'reason', label: '申请事由', type: 'text', required: true },
        { key: 'amount', label: '金额', type: 'number', required: false },
      ],
    }),
  )

async function saveFormSchema() {
  error.value = ''
  notice.value = ''
  try {
    await saveForm(formKey.value, { formName: formName.value, schemaJson: formSchema.value })
    notice.value = `表单 ${formKey.value} 已保存`
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function importXml() {
  error.value = ''
  notice.value = ''
  try {
    await modeler.importXML(xmlText.value || STARTER_XML)
    modeler.get('canvas').zoom('fit-viewport')
    notice.value = 'XML 已导入画布'
  } catch (e) {
    const msg = e?.message || String(e)
    error.value = /no diagram to display/i.test(msg)
      ? '导入失败：这份 XML 只有语义定义，缺少 <bpmndi:BPMNDiagram> 图形信息，画布无法显示'
      : `导入失败：${msg}`
  }
}

async function exportXml() {
  const { xml } = await modeler.saveXML({ format: true })
  xmlText.value = xml
  showXml.value = true
}

async function deploy() {
  error.value = ''
  notice.value = ''
  try {
    const { xml } = await modeler.saveXML({ format: true })
    await deployProcess(deployName.value, xml)
    notice.value = `已部署：${deployName.value}`
    await load()
  } catch (e) {
    error.value = e.message
  }
}

function newDiagram() {
  xmlText.value = STARTER_XML
  importXml()
}

/**
 * "Open the deployed process": pulls the BPMN source the engine actually
 * deployed (not a re-serialised model) into the canvas, renames the deployment
 * target to the same key so "deploy" publishes a new version instead of
 * forking a second process.
 *
 * bpmn-js resets the command stack on import, so canUndo() is a reliable
 * "there are edits that are not deployed yet" flag — the only case where
 * replacing the canvas silently would lose work.
 */
async function openDefinition(d) {
  error.value = ''
  notice.value = ''
  if (modeler?.get('commandStack')?.canUndo()
      && !window.confirm(`画布上有未部署的修改，载入「${d.key}」会覆盖它们，继续？`)) {
    return
  }
  try {
    const def = await definitionXml(d.key)
    xmlText.value = def.xml
    if (!def.renderable) {
      // semantic-only deployment: the engine runs it, but there is nothing to
      // draw. Show the source so the admin can rebuild it instead of staring
      // at bpmn-js' "no diagram to display".
      showXml.value = true
      error.value = `「${def.processKey}」v${def.version} 部署时没有图形信息（BPMNDiagram），画布无法显示；`
        + 'XML 已展开在下方，可据此重新设计，再以同名 key 部署（会生成新版本）。'
      return
    }
    showXml.value = false
    deployName.value = d.key
    await importXml()
    await nextTick()
    fitViewport()
    notice.value = `已载入 ${def.processKey} v${def.version}，再次部署将生成 v${def.version + 1}`
  } catch (e) {
    error.value = e.message
  }
}

function fitViewport() {
  modeler?.get('canvas').zoom('fit-viewport')
}

/**
 * Fullscreen editing is a fixed overlay rather than the browser Fullscreen API:
 * every edge of the designer stays reachable (palette, canvas, properties), the
 * layout is identical for embedders and iframes, and no permission prompt is
 * involved — so it also works when this page is driven by automation.
 *
 * bpmn-js caches the SVG size and its viewbox transform, so the container must
 * be told to re-measure after the layout settles; without that the canvas keeps
 * the old width and the diagram sits off-centre.
 */
async function toggleFullscreen() {
  fullscreen.value = !fullscreen.value
  document.body.style.overflow = fullscreen.value ? 'hidden' : ''
  await nextTick()
  requestAnimationFrame(() => {
    const canvasApi = modeler?.get('canvas')
    if (!canvasApi) return
    canvasApi.resized()
    canvasApi.zoom('fit-viewport')
  })
  if (fullscreen.value) notice.value = '已进入全屏编辑 · Esc 退出'
}

// ---- placing nodes ------------------------------------------------------

/**
 * Client point → diagram point. bpmn-js keeps its own viewbox transform, so
 * the offset must be divided by the current scale; using getBoundingClientRect
 * of the canvas container (instead of the wrapper) keeps it correct while the
 * page itself is scrolled.
 */
function toDiagramPoint(clientX, clientY) {
  const canvasApi = modeler.get('canvas')
  const rect = canvasApi.getContainer().getBoundingClientRect()
  const vb = canvasApi.viewbox()
  return {
    inside: clientX >= rect.left && clientX <= rect.right && clientY >= rect.top && clientY <= rect.bottom,
    x: vb.x + (clientX - rect.left) / vb.scale,
    y: vb.y + (clientY - rect.top) / vb.scale,
  }
}

function placeNode(node, clientX, clientY) {
  if (!modeler || !node) return null
  const point = toDiagramPoint(clientX, clientY)
  if (!point.inside) return null

  const elementFactory = modeler.get('elementFactory')
  const shape = elementFactory.createShape({ type: node.type })
  const w = shape.width || 100
  const h = shape.height || 80

  const element = modeler.get('modeling').createShape(
    shape,
    { x: Math.round(point.x - w / 2), y: Math.round(point.y - h / 2) },
    modeler.get('canvas').getRootElement(),
  )
  applyPreset(element, node)
  modeler.get('selection').select(element)
  remember(element)
  notice.value = `已添加「${node.label}」`
  return element
}

function applyPreset(element, node) {
  const modeling = modeler.get('modeling')
  if (node.preset) modeling.updateProperties(element, { ...node.preset })
  if (node.countersign) setCountersign(element, true)
}

/**
 * Countersign = parallel multi-instance over ${approvers}, ending early on a
 * single veto. Same shape the W2 backend bookkeeping expects (csRejected).
 */
function setCountersign(element, on) {
  const modeling = modeler.get('modeling')
  if (!on) {
    modeling.updateProperties(element, { loopCharacteristics: undefined })
    return
  }
  const moddle = modeler.get('moddle')
  const loop = moddle.create('bpmn:MultiInstanceLoopCharacteristics', { isSequential: false })
  loop.set('flowable:collection', '${approvers}')
  loop.set('flowable:elementVariable', 'user')
  loop.set('completionCondition', moddle.create('bpmn:FormalExpression', { body: COUNTERSIGN_CONDITION }))
  modeling.updateProperties(element, { loopCharacteristics: loop })
}

// ---- palette drag / click ----------------------------------------------

function startDrag(node, evt) {
  if (evt.pointerType === 'mouse' && evt.button !== 0) return
  drag.value = { node, startX: evt.clientX, startY: evt.clientY, x: evt.clientX, y: evt.clientY, moved: false }
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragEnd, { once: true })
  window.addEventListener('pointercancel', onDragCancel, { once: true })
}

function onDragMove(evt) {
  const d = drag.value
  if (!d) return
  const moved = d.moved || Math.abs(evt.clientX - d.startX) + Math.abs(evt.clientY - d.startY) > 4
  drag.value = { ...d, x: evt.clientX, y: evt.clientY, moved }
  overCanvas.value = moved && toDiagramPoint(evt.clientX, evt.clientY).inside
}

function onDragEnd(evt) {
  window.removeEventListener('pointermove', onDragMove)
  const d = drag.value
  drag.value = null
  overCanvas.value = false
  if (!d) return
  if (!d.moved) {
    // plain click: arm the node, the next canvas click places it
    armed.value = armed.value?.key === d.node.key ? null : d.node
    return
  }
  if (!placeNode(d.node, evt.clientX, evt.clientY)) {
    notice.value = '请把节点拖到画布内'
  }
}

function onDragCancel() {
  window.removeEventListener('pointermove', onDragMove)
  drag.value = null
  overCanvas.value = false
}

function onCanvasClick(evt) {
  if (!armed.value) return
  const node = armed.value
  armed.value = null
  placeNode(node, evt.clientX, evt.clientY)
}

function onKeydown(evt) {
  if (evt.key !== 'Escape') return
  // Esc peels one layer at a time: first the armed node, then fullscreen.
  if (armed.value) {
    armed.value = null
    notice.value = ''
    return
  }
  if (fullscreen.value) toggleFullscreen()
}

// ---- selection / properties --------------------------------------------

function describe(element) {
  const bo = element.businessObject || {}
  return {
    id: element.id,
    type: bo.$type,
    name: bo.name || '',
    candidateGroups: bo.$attrs?.['flowable:candidateGroups'] || '',
    countersign: !!bo.loopCharacteristics,
  }
}

function remember(element) {
  selected.value = describe(element)
}

function currentElement() {
  return selected.value ? modeler.get('elementRegistry').get(selected.value.id) : null
}

function applyName() {
  const element = currentElement()
  if (element) modeler.get('modeling').updateProperties(element, { name: selected.value.name })
}

function applyCandidates() {
  const element = currentElement()
  if (element) {
    modeler.get('modeling').updateProperties(element, {
      'flowable:candidateGroups': selected.value.candidateGroups,
    })
  }
}

function applyCountersign() {
  const element = currentElement()
  if (!element) return
  setCountersign(element, selected.value.countersign)
  if (selected.value.countersign) {
    modeler.get('modeling').updateProperties(element, { 'flowable:assignee': '${user}' })
  }
  remember(element)
}

const fmtTime = (iso) => (iso ? new Date(iso).toLocaleString() : '—')

onMounted(async () => {
  modeler = new BpmnModeler({ container: canvas.value })
  modeler.on('element.click', (evt) => remember(evt.element))
  // Any container resize (fullscreen toggle, window resize, XML panel opening)
  // must reach bpmn-js, otherwise it draws against a stale viewport size.
  canvasObserver = new ResizeObserver(() => modeler?.get('canvas').resized())
  canvasObserver.observe(canvas.value)
  window.addEventListener('keydown', onKeydown)
  await importXml()
  await load()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('pointermove', onDragMove)
  canvasObserver?.disconnect()
  document.body.style.overflow = ''
  modeler?.destroy()
})
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <h3 style="margin: 0;">流程管理</h3>
      <button style="width: auto; margin: 0 0 0 auto; padding: 8px 18px;" @click="load">刷新</button>
    </div>
    <p class="muted">
      节点已按审批场景预置：审批节点带 <code>flowable:candidateGroups</code>，会签节点是
      <code>${approvers}</code> 上的并行多实例（<code>csRejected</code> 一票否决），AI 预审调用
      <code>${aiReviewDelegate}</code>。
    </p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="ok">{{ notice }}</p>

    <div class="designer-shell" :class="{ fullscreen }">
      <div class="designer-head">
        <h3 style="margin: 0;">流程设计器</h3>
        <code class="head-key">{{ deployName || 'my-approval' }}</code>
        <button class="mini ghost" style="width: auto; margin: 0 0 0 auto;" @click="toggleFullscreen">
          退出全屏（Esc）
        </button>
      </div>

      <div class="designer-bar">
        <input v-model="deployName" style="width: 200px;" placeholder="部署名称" />
        <button class="mini" style="width: auto;" @click="newDiagram">新建</button>
        <button class="mini" style="width: auto;" @click="exportXml">导出 XML</button>
        <button class="mini" style="width: auto;" @click="showXml = !showXml">
          {{ showXml ? '隐藏 XML' : '编辑 XML' }}
        </button>
        <button class="mini" style="width: auto;" @click="importXml">导入 XML</button>
        <button v-if="!fullscreen" class="mini ghost" style="width: auto;" @click="toggleFullscreen">
          全屏编辑
        </button>
        <button style="width: auto; padding: 6px 16px; margin-left: auto;" @click="deploy">部署</button>
      </div>

      <div class="designer-grid">
        <div class="palette">
          <template v-for="group in NODE_GROUPS" :key="group.label">
            <p class="palette-group">{{ group.label }}</p>
            <div
              v-for="node in group.items"
              :key="node.key"
              class="palette-item"
              :class="[node.tone, { armed: armed?.key === node.key }]"
              :title="node.desc"
              @pointerdown.prevent="startDrag(node, $event)"
            >
              <span class="glyph" :class="node.tone" />
              <span class="meta">
                <b>{{ node.label }}</b>
                <small>{{ node.desc }}</small>
              </span>
            </div>
          </template>
          <p class="palette-tip">{{ hint }}</p>
        </div>

        <div class="canvas-col">
          <div class="canvas-bar">
            <span class="muted small">悬停节点边缘拖出连线 · 选中后按 Delete 删除</span>
            <button class="mini ghost" @click="fitViewport">适应画布</button>
          </div>
          <div
            ref="canvas"
            class="bpmn-canvas"
            :class="{ 'drop-active': overCanvas, armed: armed }"
            @click="onCanvasClick"
          />
          <textarea v-if="showXml" v-model="xmlText" rows="12" class="xml-box" spellcheck="false" />
        </div>

        <aside>
          <h4>节点属性</h4>
          <template v-if="selected">
            <p class="muted" style="font-size: 12px;">{{ selected.type }} · {{ selected.id }}</p>
            <label class="field">
              <span>名称</span>
              <input v-model="selected.name" @change="applyName" />
            </label>
            <template v-if="selected.type === 'bpmn:UserTask'">
              <label class="field">
                <span>候选角色（逗号分隔角色代码）</span>
                <input
                  v-model="selected.candidateGroups"
                  :disabled="selected.countersign"
                  placeholder="ADMIN"
                  @change="applyCandidates"
                />
              </label>
              <label class="check">
                <input v-model="selected.countersign" type="checkbox" @change="applyCountersign" />
                <span>会签（并行多实例，一票否决）</span>
              </label>
              <p v-if="selected.countersign" class="muted small">
                会签按流程变量 <code>approvers</code>（逗号分隔的用户名）展开，每个实例分派给
                <code>${user}</code>；任一实例驳回即以 <code>csRejected</code> 提前结束。
              </p>
            </template>
          </template>
          <p v-else class="muted">点击画布中的节点编辑属性</p>

          <h4 style="margin-top: 20px;">表单设计（{{ formKey || '未选择' }}）</h4>
          <label class="field">
            <span>表单名称</span>
            <input v-model="formName" />
          </label>
          <label class="field">
            <span>字段定义（JSON）</span>
            <textarea v-model="formSchema" rows="10" class="xml-box" spellcheck="false" />
          </label>
          <button class="mini" style="width: auto;" :disabled="!formKey" @click="saveFormSchema">
            保存表单
          </button>
        </aside>
      </div>
    </div>

    <h4 style="margin-top: 24px;">已部署流程</h4>
    <table>
      <thead>
        <tr><th>Key</th><th>名称</th><th>版本</th><th>状态</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="d in definitions" :key="d.id">
          <td><code>{{ d.key }}</code></td>
          <td>{{ d.name }}</td>
          <td>v{{ d.version }}</td>
          <td>{{ d.suspended ? '已挂起' : '启用' }}</td>
          <td>
            <button class="mini" @click="openDefinition(d)">载入设计器</button>
            <button class="mini" @click="pickForm(d.key)">编辑表单</button>
          </td>
        </tr>
        <tr v-if="!definitions.length">
          <td colspan="5" class="muted">还没有部署任何流程</td>
        </tr>
      </tbody>
    </table>
    <p class="muted" style="font-size: 12px; margin-top: 10px;">
      表单保存时间：{{ fmtTime(forms.find((f) => f.processKey === formKey)?.updatedAt) }}
    </p>

    <Teleport to="body">
      <div
        v-if="drag && drag.moved"
        class="drag-ghost"
        :class="drag.node.tone"
        :style="{ left: drag.x + 'px', top: drag.y + 'px' }"
      >
        {{ drag.node.label }}
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.designer-bar { display: flex; gap: 8px; align-items: center; margin: 12px 0; flex-wrap: wrap; }
.designer-grid {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}

/* ---- fullscreen editing ------------------------------------------------
   A fixed overlay, not the browser Fullscreen API: the palette, canvas and
   properties panel all stay in reach, Esc leaves, and the page behind is
   scroll-locked so wheel zooming the diagram never scrolls the document. */
.designer-head { display: none; }
.designer-shell.fullscreen {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  padding: 12px 16px 14px;
  background: #f6f8fc;
  overflow: hidden;
}
.designer-shell.fullscreen .designer-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}
.designer-shell.fullscreen .head-key {
  font-size: 12px;
  color: var(--muted);
  background: #eef2f7;
  border-radius: 4px;
  padding: 2px 6px;
}
.designer-shell.fullscreen .designer-bar { margin: 8px 0 10px; flex: none; }
.designer-shell.fullscreen .designer-grid {
  flex: 1;
  min-height: 0;
  align-items: stretch;
}
.designer-shell.fullscreen .palette,
.designer-shell.fullscreen aside {
  min-height: 0;
  max-height: 100%;
  overflow: auto;
}
.designer-shell.fullscreen .canvas-col {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.designer-shell.fullscreen .bpmn-canvas {
  flex: 1;
  height: auto;
  min-height: 0;
}
.designer-shell.fullscreen .xml-box { flex: none; height: 170px; }

/* ---- palette ---- */
.palette {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fbfcfe;
}
.palette-group {
  margin: 6px 0 2px;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--muted);
}
.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin: 0;
  padding: 7px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: #fff;
  color: var(--text);
  font-size: 13px;
  text-align: left;
  cursor: grab;
  user-select: none;
  touch-action: none;
  transition: border-color 0.12s, box-shadow 0.12s, transform 0.12s;
}
.palette-item:hover { border-color: var(--primary); box-shadow: 0 1px 6px rgba(37, 99, 235, 0.14); }
.palette-item:active { cursor: grabbing; transform: scale(0.98); }
.palette-item.armed { border-color: var(--primary); background: #eff6ff; }
.palette-item .meta { display: flex; flex-direction: column; line-height: 1.25; min-width: 0; }
.palette-item .meta small { color: var(--muted); font-size: 11px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.glyph { width: 12px; height: 12px; border-radius: 3px; flex: none; border: 2px solid currentColor; }
.glyph.green, .drag-ghost.green { color: #16a34a; }
.glyph.rose, .drag-ghost.rose { color: #e11d48; }
.glyph.blue, .drag-ghost.blue { color: #2563eb; }
.glyph.violet, .drag-ghost.violet { color: #7c3aed; }
.glyph.teal, .drag-ghost.teal { color: #0d9488; }
.glyph.amber, .drag-ghost.amber { color: #d97706; }
.glyph.green { border-radius: 50%; }
.glyph.rose { border-radius: 50%; border-width: 3px; }
.glyph.violet { border-radius: 3px; border-style: double; border-width: 4px; }
.glyph.amber { transform: rotate(45deg); }
.palette-tip { margin: 8px 0 0; font-size: 11px; color: var(--muted); line-height: 1.4; }

/* ---- canvas ---- */
.canvas-col { min-width: 0; }
.canvas-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; flex-wrap: wrap; }
.canvas-bar .small { font-size: 12px; white-space: nowrap; }
/* App.vue styles every <button> as a full-width form submit; the designer is a
   toolbar, so its buttons opt out of that and share one compact size. */
.designer-bar button,
.designer-head button { margin: 0; padding: 6px 14px; font-size: 13px; }
.ghost { width: auto; background: #fff; color: var(--primary); border: 1px solid var(--primary); }
.canvas-bar .ghost { margin-left: auto; padding: 4px 10px; }
.bpmn-canvas {
  height: 540px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  transition: border-color 0.12s, box-shadow 0.12s;
}
.bpmn-canvas.drop-active { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12); }
.bpmn-canvas.armed { border-style: dashed; }
/* the stock palette is replaced by the domain palette above */
.bpmn-canvas :deep(.djs-palette) { display: none; }

.drag-ghost {
  position: fixed;
  z-index: 3000;
  transform: translate(12px, 12px);
  padding: 5px 12px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid currentColor;
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.18);
  pointer-events: none;
}

.xml-box { width: 100%; font-family: ui-monospace, Consolas, monospace; font-size: 12px; margin-top: 8px; }
.field { display: block; margin-bottom: 12px; }
.field span { display: block; font-size: 13px; color: var(--muted); margin-bottom: 4px; }
.field input:disabled { background: #f3f4f6; color: var(--muted); }
.check { display: flex; align-items: center; gap: 8px; margin: 4px 0 8px; font-size: 13px; color: var(--text); }
.check input { width: auto; margin: 0; }
.small { font-size: 12px; }
</style>
