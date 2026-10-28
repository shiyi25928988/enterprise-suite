<script setup>
defineProps({ title: String })
const emit = defineEmits(['close'])
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-head">
        <h3>{{ title }}</h3>
        <button class="close" @click="emit('close')">✕</button>
      </div>
      <div class="modal-body">
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-mask {
  position: fixed; inset: 0; z-index: 50;
  background: rgba(15, 23, 42, 0.45);
  display: flex; align-items: flex-start; justify-content: center;
  padding-top: 8vh;
}
.modal {
  background: #fff; border-radius: 8px; width: 460px; max-height: 82vh;
  display: flex; flex-direction: column; overflow: hidden;
  box-shadow: 0 20px 40px rgba(0,0,0,0.15);
}
.modal-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 20px; border-bottom: 1px solid var(--border);
}
.modal-head h3 { margin: 0; font-size: 15px; }
.close { border: none; background: none; cursor: pointer; font-size: 14px; width: auto; padding: 4px; margin: 0; }
.modal-body { padding: 16px 20px 20px; overflow-y: auto; }
</style>
