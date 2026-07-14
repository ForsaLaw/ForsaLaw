  import { useEffect, useRef } from 'react'
import '../styles/SiteVideoBackground.css'

/**
 * Vidéo plein écran en boucle sous l’UI (muted + playsInline = autoplay autorisé).
 * Filigrane bas d’image (ex. PixVerse) : masqué par CSS (.site-video-watermark-mask).
 * Suppression définitive : recadrer avec FFmpeg (voir commentaire en bas du fichier CSS).
 */
export default function SiteVideoBackground() {
  const videoRef = useRef(null)

  useEffect(() => {
    const v = videoRef.current
    if (!v) return
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)')

    const apply = () => {
      if (mq.matches) {
        v.pause()
        v.classList.add('site-video-el--hidden')
      } else {
        v.classList.remove('site-video-el--hidden')
        v.play().catch(() => {})
      }
    }

    apply()
    mq.addEventListener('change', apply)
    return () => mq.removeEventListener('change', apply)
  }, [])

  return (
    <div className="site-video-wrap" aria-hidden="true">
      <video
        ref={videoRef}
        className="site-video-el"
        src="/video/forsalaw-hero-bg.mp4"
        autoPlay
        muted
        loop
        playsInline
        preload="auto"
      />
      <div className="site-video-scrim" />
      <div className="site-video-watermark-mask" />
    </div>
  )
}
