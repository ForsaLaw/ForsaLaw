/**
 * WebSocketContext.jsx
 * Manages a single STOMP-over-SockJS connection for the whole app.
 * Connects when the user has a valid JWT token, disconnects on logout.
 *
 * Backend endpoints:
 *   SockJS endpoint : /ws  (JWT sent in the STOMP CONNECT frame's Authorization header)
 *   App destination prefix: /app
 *   Broker topic prefix    : /topic
 *
 * Usage:
 *   const { subscribe, publish, connected } = useWebSocket()
 */
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuth } from './AuthContext.jsx'

const WebSocketContext = createContext(null)

export function WebSocketProvider({ children }) {
  const { token } = useAuth()
  const clientRef = useRef(null)
  const [connected, setConnected] = useState(false)
  // subscriptions map: destination -> Set<callback>
  const subCallbacksRef = useRef(new Map())
  // stomp subscription handles: destination -> stompSubscription
  const stompSubsRef = useRef(new Map())

  // ── Inner: re-subscribe all registered callbacks on reconnect ────────────
  const resubscribeAll = useCallback((client) => {
    stompSubsRef.current.clear()
    subCallbacksRef.current.forEach((callbacks, destination) => {
      if (callbacks.size === 0) return
      const sub = client.subscribe(destination, (frame) => {
        let payload
        try { payload = JSON.parse(frame.body) } catch { payload = frame.body }
        callbacks.forEach((cb) => cb(payload))
      })
      stompSubsRef.current.set(destination, sub)
    })
  }, [])

  // ── Lifecycle: create/destroy client when token changes ─────────────────
  useEffect(() => {
    if (!token) {
      // Disconnect if client exists
      if (clientRef.current && clientRef.current.active) {
        clientRef.current.deactivate()
      }
      clientRef.current = null
      stompSubsRef.current.clear()
      setConnected(false)
      return
    }

    const client = new Client({
      // Le token n'est plus dans l'URL (fuite dans les logs/historique) : il est envoye dans
      // l'en-tete Authorization de la frame STOMP CONNECT, lue cote backend par un ChannelInterceptor.
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {}, // silence debug logs in production; set to console.log to debug

      onConnect: () => {
        setConnected(true)
        resubscribeAll(client)
      },
      onDisconnect: () => {
        setConnected(false)
      },
      onStompError: (frame) => {
        console.warn('[WS] STOMP error', frame.headers?.message)
        setConnected(false)
      },
      onWebSocketClose: () => {
        setConnected(false)
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
      clientRef.current = null
      stompSubsRef.current.clear()
      setConnected(false)
    }
  }, [token, resubscribeAll])

  // ── subscribe(destination, callback) → unsubscribe fn ───────────────────
  const subscribe = useCallback((destination, callback) => {
    // Register callback
    if (!subCallbacksRef.current.has(destination)) {
      subCallbacksRef.current.set(destination, new Set())
    }
    const callbacks = subCallbacksRef.current.get(destination)
    callbacks.add(callback)

    // If STOMP is connected and no STOMP-sub for this destination yet → subscribe now
    const client = clientRef.current
    if (client?.active && client?.connected && !stompSubsRef.current.has(destination)) {
      const sub = client.subscribe(destination, (frame) => {
        let payload
        try { payload = JSON.parse(frame.body) } catch { payload = frame.body }
        subCallbacksRef.current.get(destination)?.forEach((cb) => cb(payload))
      })
      stompSubsRef.current.set(destination, sub)
    }

    // Return unsubscribe function
    return () => {
      callbacks.delete(callback)
      // If no more callbacks for this destination → unsubscribe from STOMP too
      if (callbacks.size === 0) {
        stompSubsRef.current.get(destination)?.unsubscribe()
        stompSubsRef.current.delete(destination)
        subCallbacksRef.current.delete(destination)
      }
    }
  }, [])

  // ── publish(destination, body) ───────────────────────────────────────────
  const publish = useCallback((destination, body) => {
    const client = clientRef.current
    if (!client?.active || !client?.connected) return
    client.publish({
      destination,
      body: typeof body === 'string' ? body : JSON.stringify(body),
    })
  }, [])

  return (
    <WebSocketContext.Provider value={{ subscribe, publish, connected }}>
      {children}
    </WebSocketContext.Provider>
  )
}

export function useWebSocket() {
  const ctx = useContext(WebSocketContext)
  if (!ctx) throw new Error('useWebSocket must be used within WebSocketProvider')
  return ctx
}
