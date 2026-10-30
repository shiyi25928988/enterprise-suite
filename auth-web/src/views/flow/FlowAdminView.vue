<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import { listDefinitions, deployProcess, listForms, saveForm } from '../../api/flow'

const EMPTY_XML = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
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
</definitions>`

const canvas = ref(null)
const definitions = ref([])
const forms = ref([])
const error = ref('')
const notice = ref('')

const deployName = ref('my-approval')
const xmlText = ref('')
const showXml = ref(false)

const selected = ref(null)      // { id, type, name, candidateGroups }
const formKey = ref('')
const formName = ref('')
const formSchema = ref('')
let modeler = null

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
    await modeler.importXML(xmlText.value || EMPTY_XML)
    modeler.get('canvas').zoom('fit-viewport')
    notice.value = 'XML 已导入画布'
  } catch (e) {
    error.value = `导入失败：${e.message || e}`
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
  xmlText.value = EMPTY_XML
  importXml()
}

onMounted(async () => {
  modeler = new BpmnModeler({ container: canvas.value })
  modeler.on('element.click', (evt) => {
    const el = evt.element
    const bo = el.businessObject || {}
    selected.value = {
      id: el.id,
      type: bo.$type,
      name: bo.name || '',
      candidateGroups: bo.$attrs?.['flowable:candidateGroups'] || '',
    }
  })
  await importXml()
  await load()
})

onBeforeUnmount(() => {
  modeler?.destroy()
})

async function applyName() {
  if (!selected.value) return
  const element = modeler.get('elementRegistry').get(selected.value.id)
  if (!element) return
  modeler.get('modeling').updateProperties(element, { name: selected.value.name })
}

async function applyCandidates() {
  if (!selected.value) return
  const element = modeler.get('elementRegistry').get(selected.value.id)
  if (!element) return
  modeler.get('modeling').updateProperties(element, {
    'flowable:candidateGroups': selected.value.candidateGroups,
  })
}

const fmtTime = (iso) => (iso ? new Date(iso).toLocaleString() : '—')
</script>

<template>
  <div class="card">
    <div class="toolbar">
      <h3 style="margin: 0;">流程管理</h3>
      <button style="width: auto; margin: 0 0 0 auto; padding: 8px 18px;" @click="load">刷新</button>
    </div>
    <p class="muted">
      BPMN 2.0 设计器基于 bpmn.io。审批人：<code>flowable:candidateGroups="角色代码"</code>
      （平台角色，如 ADMIN）；AI 预审节点使用服务任务
      <code>flowable:delegateExpression="${aiReviewDelegate}"</code>。
    </p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="ok">{{ notice }}</p>

    <div class="designer-bar">
      <input v-model="deployName" style="width: 200px;" placeholder="部署名称" />
      <button class="mini" style="width: auto;" @click="newDiagram">新建</button>
      <button class="mini" style="width: auto;" @click="exportXml">导出 XML</button>
      <button class="mini" style="width: auto;" @click="showXml = !showXml">
        {{ showXml ? '隐藏 XML' : '编辑 XML' }}
      </button>
      <button class="mini" style="width: auto;" @click="importXml">导入 XML</button>
      <button style="width: auto; margin: 0 0 0 auto; padding: 6px 16px;" @click="deploy">
        部署
      </button>
    </div>

    <div class="designer-grid">
      <div>
        <div ref="canvas" class="bpmn-canvas" />
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
          <label v-if="selected.type === 'bpmn:UserTask'" class="field">
            <span>候选角色（逗号分隔角色代码）</span>
            <input v-model="selected.candidateGroups" placeholder="ADMIN" @change="applyCandidates" />
          </label>
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
  </div>
</template>

<style scoped>
.designer-bar { display: flex; gap: 8px; align-items: center; margin: 12px 0; flex-wrap: wrap; }
.designer-grid { display: grid; grid-template-columns: 1fr 320px; gap: 20px; }
.bpmn-canvas { height: 460px; border: 1px solid var(--border); border-radius: 8px; background: #fff; }
.xml-box { width: 100%; font-family: ui-monospace, Consolas, monospace; font-size: 12px; margin-top: 8px; }
.field { display: block; margin-bottom: 12px; }
.field span { display: block; font-size: 13px; color: var(--muted); margin-bottom: 4px; }
</style>
