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
/** Process key of what is on the canvas — the id Flowable deploys under. */
const processKey = ref('')
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
/** id of the just-created branch, flashed + focused until the timer clears it */
const focusBranchId = ref(null)
let modeler = null
let canvasObserver = null
let focusTimer = null
/** suppresses branch-focus detection while an import replays the diagram */
let importing = false

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
  // Import replays the whole diagram through the command stack, which would
  // otherwise look like "a branch was just added" to syncBranchList and steal
  // focus into a stale panel — gated off while an import is in flight.
  importing = true
  try {
    await modeler.importXML(xmlText.value || STARTER_XML)
    modeler.get('canvas').zoom('fit-viewport')
    refreshProcessKey()
    notice.value = 'XML 已导入画布'
  } catch (e) {
    const msg = e?.message || String(e)
    error.value = /no diagram to display/i.test(msg)
      ? '导入失败：这份 XML 只有语义定义，缺少 <bpmndi:BPMNDiagram> 图形信息，画布无法显示'
      : `导入失败：${msg}`
  } finally {
    importing = false
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
  // Gate before the round trip: these are the failures the engine would only
  // report at runtime, on somebody else's instance.
  const { blocking, warnings } = lintBranches()
  if (blocking.length) {
    error.value = `未部署：${blocking.join('；')}`
    return
  }
  if (warnings.length && !window.confirm(`部署前检查发现 ${warnings.length} 条提示：\n\n${warnings.join('\n')}\n\n仍然部署？`)) {
    return
  }
  try {
    const { xml } = await modeler.saveXML({ format: true })
    // The deployment name is just a label on the deployment; Flowable takes the
    // process key from <process id="…"> inside the XML. Echoing the key back is
    // the only way an admin can see which process they actually published.
    const info = await deployProcess(deployName.value, xml)
    deployName.value = info.key
    notice.value = `已部署：${info.key} v${info.version}`
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
 * Flowable keys a deployment by <process id="…">, not by the deployment name.
 * Surfacing the key makes "I renamed the deployment, why is there a second
 * process now" impossible to walk into.
 */
function refreshProcessKey() {
  const root = modeler?.get('canvas').getRootElement()
  processKey.value = root?.businessObject?.id || ''
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

const EXCLUSIVE_GATEWAY = 'bpmn:ExclusiveGateway'

/**
 * Branch conditions live on the sequence flow, not on the gateway: an
 * exclusive gateway only decides by evaluating each outgoing flow's
 * conditionExpression in document order and taking the first truthy one.
 * That is why selecting a gateway has to surface its whole branch list —
 * editing it node-by-node would mean clicking a 2px-wide line.
 *
 * The list comes from the diagram element's own outgoing connections, not from
 * businessObject.outgoing: bpmn-moddle only fills that reference when the XML
 * spells out redundant <outgoing> children, while sourceRef/targetRef alone is
 * perfectly valid BPMN — and is what every hand-written file contains.
 */
function branchesOf(element) {
  const bo = element.businessObject || {}
  const defaultId = bo.default?.id
  return (element.outgoing || [])
    .filter((connection) => connection.type === 'bpmn:SequenceFlow')
    .map((connection) => {
      const flow = connection.businessObject
      return {
        id: connection.id,
        name: flow.name || '',
        target: connection.target?.businessObject?.name || flow.targetRef?.id || '—',
        condition: flow.conditionExpression?.body || '',
        isDefault: connection.id === defaultId,
      }
    })
}

function describe(element) {
  const bo = element.businessObject || {}
  const type = bo.$type || ''
  return {
    id: element.id,
    type,
    name: bo.name || '',
    candidateGroups: bo.$attrs?.['flowable:candidateGroups'] || '',
    countersign: !!bo.loopCharacteristics,
    isFlow: type === 'bpmn:SequenceFlow',
    isGateway: type === EXCLUSIVE_GATEWAY,
    condition: bo.conditionExpression?.body || '',
    branches: type === EXCLUSIVE_GATEWAY ? branchesOf(element) : [],
  }
}

function remember(element) {
  selected.value = describe(element)
  highlightBranches(element)
}

/**
 * Marks every outgoing flow of the selected gateway, so "the two lines you
 * have to configure" are visible without hunting for them.
 */
let markedFlows = []
function highlightBranches(element) {
  const canvasApi = modeler?.get('canvas')
  const registry = modeler?.get('elementRegistry')
  if (!canvasApi || !registry) return
  markedFlows.forEach((id) => {
    if (registry.get(id)) canvasApi.removeMarker(id, 'flow-branch')
  })
  markedFlows = []
  if (!element || element.businessObject?.$type !== EXCLUSIVE_GATEWAY) return
  ;(element.outgoing || []).forEach((connection) => {
    if (registry.get(connection.id)) {
      canvasApi.addMarker(connection.id, 'flow-branch')
      markedFlows.push(connection.id)
    }
  })
}

/**
 * Redraws the branch list only when a branch was added or removed. Rebuilding
 * on every command would replace the selected object and steal focus from the
 * input the admin is typing in.
 *
 * A newly added branch (native edge-drag, or the "new branch" button) also
 * grabs focus into its name field: a fresh sequence flow is unnamed and
 * unconditional, which deploy lint rejects — so the panel walks the admin
 * straight into fixing it instead of leaving a red line for later.
 */
function syncBranchList() {
  const current = selected.value
  if (!current?.isGateway) return
  const element = modeler?.get('elementRegistry').get(current.id)
  if (!element) return
  const ids = (element.outgoing || []).map((connection) => connection.id).join(',')
  if (ids !== current.branches.map((branch) => branch.id).join(',')) {
    const known = new Set(current.branches.map((branch) => branch.id))
    remember(element)
    if (!importing) {
      const added = (element.outgoing || []).map((c) => c.id).find((id) => !known.has(id))
      if (added) focusBranch(added)
    }
  }
}

/** Flash + focus the name field of a just-created branch for a moment. */
function focusBranch(id) {
  focusBranchId.value = id
  nextTick(() => {
    document.querySelector(`input[data-branch="${id}"]`)?.focus()
  })
  clearTimeout(focusTimer)
  focusTimer = setTimeout(() => {
    if (focusBranchId.value === id) focusBranchId.value = null
  }, 2600)
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

// ---- branch conditions --------------------------------------------------

function flowById(id) {
  return modeler?.get('elementRegistry').get(id) || null
}

function writeCondition(flowId, raw) {
  const flow = flowById(flowId)
  if (!flow) return
  const body = (raw || '').trim()
  modeler.get('modeling').updateProperties(flow, {
    conditionExpression: body
      ? modeler.get('moddle').create('bpmn:FormalExpression', { body })
      : undefined,
  })
}

function applyFlowName(flowId, name) {
  const flow = flowById(flowId)
  if (flow) modeler.get('modeling').updateProperties(flow, { name })
}

/** Condition edited from the gateway's branch list. */
function applyBranchCondition(branch) {
  writeCondition(branch.id, branch.condition)
}

/** Condition edited by selecting the sequence flow itself. */
function applySelectedCondition() {
  writeCondition(selected.value.id, selected.value.condition)
}

/**
 * Marking a flow as the default branch. BPMN forbids a condition on the
 * default flow, and Flowable silently ignores one, so any condition left on it
 * would read as "this branch is conditional" while it actually is the
 * fallback — clear it instead of leaving a lie on the diagram.
 */
function applyDefaultFlow(branch) {
  const gateway = currentElement()
  const flow = flowById(branch.id)
  if (!gateway || !flow) return
  const modeling = modeler.get('modeling')
  modeling.updateProperties(gateway, { default: flow.businessObject })
  modeling.updateProperties(flow, { conditionExpression: undefined })
  remember(gateway)
  notice.value = `默认分支已设为「${branch.target}」`
}

function clearDefaultFlow() {
  const gateway = currentElement()
  if (!gateway) return
  modeler.get('modeling').updateProperties(gateway, { default: undefined })
  remember(gateway)
}

/**
 * One-click branch: drops a preset approval task below the gateway's existing
 * targets, wires it up and focuses its name field. Native edge-dragging still
 * works — this covers the case where the admin has to aim a rubber band at a
 * 36px hit area first; the button removes the aiming entirely.
 */
async function addBranch() {
  const gateway = currentElement()
  if (!gateway || gateway.businessObject?.$type !== EXCLUSIVE_GATEWAY) return
  const modeling = modeler.get('modeling')
  const shape = modeler.get('elementFactory').createShape({ type: 'bpmn:UserTask' })
  const w = shape.width || 100
  const h = shape.height || 80
  // stack below the lowest existing target so branches read top-to-bottom in
  // the order the gateway evaluates them
  const bottoms = gateway.outgoing
    .filter((c) => c.type === 'bpmn:SequenceFlow' && c.target)
    .map((c) => c.target.y + c.target.height)
  const x = gateway.x + gateway.width + 160
  const y = bottoms.length ? Math.max(...bottoms) + 60 : gateway.y + gateway.height - h / 2

  const target = modeling.createShape(shape, { x: Math.round(x), y: Math.round(y) },
    modeler.get('canvas').getRootElement())
  applyPreset(target, NODE_GROUPS.flatMap((g) => g.items).find((n) => n.key === 'approve'))
  const connection = modeling.connect(gateway, target)
  modeler.get('selection').select(target)
  // commandStack.changed → syncBranchList re-renders the list; focus lands on
  // the new name field once Vue flushes the DOM
  focusBranch(connection.id)
  await nextTick()
  notice.value = '已创建新分支：命名并设置条件后才能部署'
}

/**
 * Deploy-time lint. Every rule here was measured against Flowable 8 rather than
 * reasoned about, because the obvious guesses are wrong:
 *
 *   ${amount > 1000} + default  → condition true takes the branch, false falls
 *                                 through to the default flow
 *   amount > 1000 (no ${})      → the instance fails to start (HTTP 500): the
 *                                 engine evaluates the bare text as an
 *                                 expression, not as a fixed truthy value
 *   draft that omits ${}        → same failure, discovered by the employee
 *                                 submitting the form, not by the admin
 *   a non-default unconditional → it is selected because "no condition" reads
 *                                 as true, so any branch ordered after it is
 *                                 dead code
 *   no branch matches, no       → the instance fails to start: "no outgoing
 *   default                       sequence flow could be selected"
 *   default flow with a         → the deployment is rejected outright
 *   condition
 *
 * All of them parse and deploy cleanly, which is exactly why they are worth
 * blocking at design time.
 */
function lintBranches() {
  const registry = modeler.get('elementRegistry')
  const blocking = []
  const warnings = []
  // filter on the diagram element type, not on businessObject.$type: a named
  // gateway gets an external label element that shares the very same
  // businessObject and has no outgoing connections of its own
  registry.filter((el) => el.type === EXCLUSIVE_GATEWAY && !el.labelTarget).forEach((gw) => {
    const bo = gw.businessObject
    const label = bo.name || bo.id
    const flows = (gw.outgoing || []).filter((c) => c.type === 'bpmn:SequenceFlow')
    if (!flows.length) {
      blocking.push(`网关「${label}」没有出向分支`)
      return
    }
    if (flows.length === 1) {
      warnings.push(`网关「${label}」只有一条出向分支，条件不成立时流程会卡在这里`)
    }
    const defaultId = bo.default?.id
    flows.forEach((connection) => {
      const flow = connection.businessObject
      const body = (flow.conditionExpression?.body || '').trim()
      const to = connection.target?.businessObject?.name || flow.targetRef?.id
      if (connection.id === defaultId) {
        if (body) {
          blocking.push(`网关「${label}」的默认分支「${to}」不能再带条件，Flowable 会拒绝部署`)
        }
        return
      }
      if (!body) {
        blocking.push(`网关「${label}」→「${to}」没有条件：无条件的出向流会被直接走通，`
          + '排在它后面的分支永远到不了（要兜底请把它设为默认分支）')
      } else if (!/[#$]\{/.test(body)) {
        blocking.push(`网关「${label}」→「${to}」的条件 ${body} 不是表达式，`
          + '请写成 ${…}；否则流程走到网关时会启动失败')
      }
    })
    if (flows.length > 1 && !defaultId) {
      warnings.push(`网关「${label}」没有默认分支，所有条件都不成立时流程会在网关处失败`)
    }
  })
  return { blocking, warnings }
}

const fmtTime = (iso) => (iso ? new Date(iso).toLocaleString() : '—')

onMounted(async () => {
  modeler = new BpmnModeler({ container: canvas.value })
  // clicking a node's label must edit the node, not the label element
  // (labels share the businessObject but have no connections and no bounds)
  modeler.on('element.click', (evt) => remember(evt.element.labelTarget || evt.element))
  modeler.on('commandStack.changed', () => {
    syncBranchList()
    refreshProcessKey()
  })
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
        <code class="head-key">{{ processKey || '—' }}</code>
        <button class="mini ghost" style="width: auto; margin: 0 0 0 auto;" @click="toggleFullscreen">
          退出全屏（Esc）
        </button>
      </div>

      <div class="designer-bar">
        <input v-model="deployName" style="width: 200px;" placeholder="部署包名称" />
        <code class="key-chip" title="流程 key 取自 XML 里的 process id，部署包名称不决定它">
          key: {{ processKey || '—' }}
        </code>
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

            <template v-if="selected.isFlow">
              <label class="field">
                <span>条件表达式</span>
                <input
                  v-model="selected.condition"
                  placeholder="${approved == true}"
                  @change="applySelectedCondition"
                />
              </label>
              <p class="muted small">
                走到这条分支前求值。必须写成 <code>${…}</code>：裸写文本时 Flowable 求值会失败，
                流程在网关处就起不来。
              </p>
            </template>

            <template v-if="selected.isGateway">
              <p class="muted small">
                排他网关按顺序求值每条出向分支，取第一个为真的；都不成立时走<b>默认分支</b>。
              </p>
              <div v-for="b in selected.branches" :key="b.id" class="branch">
                <div class="branch-head">
                  <input
                    v-model="b.name"
                    class="branch-name"
                    :class="{ 'branch-focus': focusBranchId === b.id }"
                    :data-branch="b.id"
                    placeholder="分支名称（通过 / 驳回）"
                    @change="applyFlowName(b.id, b.name)"
                  />
                  <label class="branch-default" :title="`把「${b.target}」设为默认分支`">
                    <input
                      type="radio"
                      name="defaultBranch"
                      :checked="b.isDefault"
                      @change="applyDefaultFlow(b)"
                    />
                    <span>默认</span>
                  </label>
                </div>
                <input
                  v-model="b.condition"
                  :disabled="b.isDefault"
                  class="branch-cond"
                  :placeholder="b.isDefault ? '默认分支不参与条件判定' : '${approved == true}'"
                  @change="applyBranchCondition(b)"
                />
                <p class="branch-target">→ {{ b.target }}</p>
              </div>
              <p v-if="!selected.branches.length" class="muted small">
                这个网关还没有出向分支：从网关边缘拖出连线连到下一个节点，或直接点下方按钮新建。
              </p>
              <button
                class="mini ghost branch-add"
                style="width: auto;"
                title="在网关下方创建一个审批节点并连为出向分支"
                @click="addBranch"
              >
                ＋ 新建分支
              </button>
              <button
                v-if="selected.branches.some((b) => b.isDefault)"
                class="mini ghost"
                style="width: auto;"
                @click="clearDefaultFlow"
              >
                取消默认分支
              </button>
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
.key-chip {
  font-size: 12px;
  color: var(--muted);
  background: #eef2f7;
  border-radius: 4px;
  padding: 2px 6px;
  white-space: nowrap;
}
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

/* ---- branch conditions -------------------------------------------------
   Each outgoing flow of the selected gateway gets a card; the flows
   themselves are marked on the canvas (see highlightBranches). */
.branch {
  margin-bottom: 8px;
  padding: 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: #fff;
}
.branch-head { display: flex; align-items: center; gap: 6px; }
.branch-head .branch-name { flex: 1; min-width: 0; }
.branch-default {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
}
.branch-default input { width: auto; margin: 0; }
.branch-cond {
  width: 100%;
  margin-top: 6px;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
}
.branch-cond:disabled { background: #f3f4f6; color: var(--muted); }
.branch-target { margin: 4px 0 0; font-size: 11px; color: var(--muted); }
.branch-add { margin-top: 2px; padding: 4px 10px; }
/* freshly created branch: the name field flashes so the admin sees where to type */
.branch-focus {
  outline: none;
  border-color: var(--primary);
  animation: branch-flash 1.3s ease-out 2;
}
@keyframes branch-flash {
  0%, 100% { box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.22); }
  50% { box-shadow: none; }
}
.bpmn-canvas :deep(.flow-branch .djs-visual > path) {
  stroke: #d97706;
  stroke-width: 2.5px;
}
</style>
