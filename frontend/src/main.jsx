import './index.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

function showFatal(rootEl, err) {
  const msg = err?.message || String(err)
  const stack = err?.stack || ''
  console.error('[ForsaLaw bootstrap]', err)
  rootEl.innerHTML = ''
  const wrap = document.createElement('div')
  wrap.setAttribute(
    'style',
    'min-height:100vh;padding:2rem;background:#121212;color:#f5f5f5;font-family:system-ui,sans-serif;max-width:42rem;margin:0 auto',
  )
  const title = document.createElement('p')
  title.setAttribute('style', 'color:#e8c76b;letter-spacing:0.15em;font-size:0.7rem;margin-bottom:1rem')
  title.textContent = 'ÉCHEC DU CHARGEMENT (module)'
  const hint = document.createElement('p')
  hint.setAttribute('style', 'color:rgba(255,255,255,0.75);margin-bottom:1rem;line-height:1.5')
  hint.textContent =
    'Un import a échoué avant React. Vérifie aussi F12 → Console. Si le projet est dans OneDrive, teste une copie hors OneDrive.'
  const pre = document.createElement('pre')
  pre.setAttribute(
    'style',
    'padding:1rem;background:#000;border:1px solid rgba(232,199,107,0.35);font-size:0.78rem;overflow:auto;white-space:pre-wrap;word-break:break-word',
  )
  pre.textContent = `${msg}\n\n${stack}`
  wrap.appendChild(title)
  wrap.appendChild(hint)
  wrap.appendChild(pre)
  rootEl.appendChild(wrap)
}

async function boot() {
  const rootEl = document.getElementById('root')
  if (!rootEl) {
    document.body.innerHTML =
      '<p style="padding:2rem;font-family:sans-serif;background:#121212;color:#fff">Élément #root introuvable.</p>'
    return
  }

  try {
    const { i18nReady } = await import('./i18n.js')
    const [
      { BrowserRouter },
      { default: App },
      { default: RootErrorBoundary },
      { AuthProvider },
      { WebSocketProvider },
    ] = await Promise.all([
      import('react-router-dom'),
      import('./App.jsx'),
      import('./components/RootErrorBoundary.jsx'),
      import('./context/AuthContext.jsx'),
      import('./context/WebSocketContext.jsx'),
    ])

    // Attendre le chargement de la langue par defaut (fr) avant le premier rendu.
    await i18nReady

    createRoot(rootEl).render(
      <StrictMode>
        <RootErrorBoundary>
          <BrowserRouter>
            <AuthProvider>
              <WebSocketProvider>
                <App />
              </WebSocketProvider>
            </AuthProvider>
          </BrowserRouter>
        </RootErrorBoundary>
      </StrictMode>,
    )
  } catch (err) {
    showFatal(rootEl, err)
  }
}

boot()

window.addEventListener('unhandledrejection', (event) => {
  console.error('[unhandledrejection]', event.reason)
  const rootEl = document.getElementById('root')
  if (rootEl && rootEl.childElementCount === 0) {
    showFatal(rootEl, event.reason instanceof Error ? event.reason : new Error(String(event.reason)))
  }
})
