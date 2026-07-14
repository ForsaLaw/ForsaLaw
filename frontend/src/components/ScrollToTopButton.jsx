import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import scalesImg from '../assets/scroll-to-top-scales.png'
import '../styles/ScrollToTopButton.css'

const SCROLL_THRESHOLD = 320

export default function ScrollToTopButton() {
  const { t } = useTranslation()
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const onScroll = () => setVisible(window.scrollY > SCROLL_THRESHOLD)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  const goTop = useCallback(() => {
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches
    window.scrollTo({ top: 0, left: 0, behavior: reduce ? 'instant' : 'smooth' })
  }, [])

  if (!visible) return null

  return (
    <button
      type="button"
      className="scroll-to-top-scales"
      onClick={goTop}
      aria-label={t('scroll_to_top_aria')}
    >
      <img src={scalesImg} alt="" width={56} height={56} decoding="async" />
    </button>
  )
}
