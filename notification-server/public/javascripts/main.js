// Simple client for testing Notification Server endpoints

function logApi(msg) {
  const el = document.getElementById('apiLog')
  el.textContent = el.textContent + `${new Date().toISOString()} - ${msg}\n`
  const container = el.parentElement
  if (container) container.scrollTop = container.scrollHeight
}

function logWs(msg) {
  const el = document.getElementById('wsLog')
  el.textContent = el.textContent + `${new Date().toISOString()} - ${msg}\n`
  const container = el.parentElement
  if (container) container.scrollTop = container.scrollHeight
}

document.addEventListener('DOMContentLoaded', () => {
  const createForm = document.getElementById('createEventForm')
  createForm.addEventListener('submit', async (e) => {
    e.preventDefault()
    const data = Object.fromEntries(new FormData(createForm))
    // normalize numbers
    ['parkingSearches','slotViews','bookingAttempts','avgScrollDepth','sessionDuration'].forEach(k => { if (data[k] !== undefined) data[k] = parseInt(data[k]||0) })
    if (!data.lastActivity) delete data.lastActivity
    const res = await fetch('/api/events', {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(data)
    })
    const text = await res.text()
    logApi(`POST /api/events -> ${res.status} ${text}`)
  })

  const stopForm = document.getElementById('stopStreamForm')
  stopForm.addEventListener('submit', async (e) => {
    e.preventDefault()
    const fd = new FormData(stopForm)
    const streamId = fd.get('streamId')
    // Include Content-Type to satisfy Play CSRF check for POST
    const res = await fetch(`/api/events/stop/${encodeURIComponent(streamId)}`, { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({}) })
    const text = await res.text()
    logApi(`POST /api/events/stop/${streamId} -> ${res.status} ${text}`)
  })

  const statusForm = document.getElementById('statusForm')
  statusForm.addEventListener('submit', async (e) => {
    e.preventDefault()
    const fd = new FormData(statusForm)
    const streamId = fd.get('streamId')
    const res = await fetch(`/api/events/status/${encodeURIComponent(streamId)}`)
    const text = await res.text()
    logApi(`GET /api/events/status/${streamId} -> ${res.status} ${text}`)
  })

  let socket = null
  document.getElementById('wsConnect').addEventListener('click', () => {
    if (socket) return
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    socket = new WebSocket(`${proto}://${location.host}/api/events/ws`)
    socket.onopen = () => { logWs('WS connected'); document.getElementById('wsDisconnect').disabled = false }
    socket.onmessage = (m) => logWs('MSG: ' + m.data)
    socket.onclose = () => { logWs('WS closed'); socket = null; document.getElementById('wsDisconnect').disabled = true }
    socket.onerror = (e) => logWs('WS error')
  })

  document.getElementById('wsDisconnect').addEventListener('click', () => {
    if (!socket) return
    socket.close()
  })

  // Notifications API handlers
  document.getElementById('listNotifications').addEventListener('click', async () => {
    const res = await fetch('/api/notifications')
    const text = await res.text()
    logApi(`GET /api/notifications -> ${res.status} ${text}`)
  })

  const getNotifForm = document.getElementById('getNotificationForm')
  getNotifForm.addEventListener('submit', async (e) => {
    e.preventDefault()
    const id = new FormData(getNotifForm).get('id')
    const res = await fetch(`/api/notifications/${encodeURIComponent(id)}`)
    const text = await res.text()
    logApi(`GET /api/notifications/${id} -> ${res.status} ${text}`)
  })

  const createNotifForm = document.getElementById('createNotificationForm')
  createNotifForm.addEventListener('submit', async (e) => {
    e.preventDefault()
    const data = Object.fromEntries(new FormData(createNotifForm))
    data.createdAt = new Date().toISOString()
    const res = await fetch('/api/notifications', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(data) })
    const text = await res.text()
    logApi(`POST /api/notifications -> ${res.status} ${text}`)
  })

  const updateStatusForm = document.getElementById('updateStatusForm')
  updateStatusForm.addEventListener('submit', async (e) => {
    e.preventDefault()
    const fd = new FormData(updateStatusForm)
    const id = fd.get('id')
    const status = fd.get('status')
    const res = await fetch(`/api/notifications/${encodeURIComponent(id)}/status`, { method: 'PUT', headers: {'Content-Type':'application/json'}, body: JSON.stringify({status}) })
    const text = await res.text()
    logApi(`PUT /api/notifications/${id}/status -> ${res.status} ${text}`)
  })
})
