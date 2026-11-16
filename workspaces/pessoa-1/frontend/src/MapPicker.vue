<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
const emit = defineEmits<{ select: [latitude: number, longitude: number] }>()
const element = ref<HTMLElement | null>(null)
const unavailable = ref(false)
let map: L.Map | undefined
onMounted(() => {
  try {
    map = L.map(element.value!).setView([-15.78, -47.93], 4)
    const tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '&copy; OpenStreetMap contributors', maxZoom: 19 })
    tiles.on('tileerror', () => { unavailable.value = true })
    tiles.addTo(map)
    let marker: L.CircleMarker | undefined
    map.on('click', (event: L.LeafletMouseEvent) => {
      marker?.remove(); marker = L.circleMarker(event.latlng).addTo(map!)
      emit('select', Number(event.latlng.lat.toFixed(6)), Number(event.latlng.lng.toFixed(6)))
    })
  } catch { unavailable.value = true }
})
onUnmounted(() => map?.remove())
</script>
<template><div><p>Clique no mapa para selecionar o destino. Sem geocodificação; também é possível digitar as coordenadas.</p><div ref="element" class="map" aria-label="Mapa para seleção de coordenadas do destino"></div><p v-if="unavailable" role="status">Mapa indisponível. Informe latitude e longitude manualmente; a entrada numérica funciona sem conexão.</p></div></template>
