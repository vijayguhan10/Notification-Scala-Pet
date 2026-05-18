// Simple client for testing Notification Server endpoints

function logApi(msg) {
  const el = document.getElementById('apiLog')
  el.textContent = `${new Date().toISOString()} - ${msg}\n` + el.textContent
}

function logWs(msg) {
  const el = document.getElementById('wsLog')
  el.textContent = `${new Date().toISOString()} - ${msg}\n` + el.textContent
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
    const res = await fetch(`/api/events/stop/${encodeURIComponent(streamId)}`, { method: 'POST' })
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
})
