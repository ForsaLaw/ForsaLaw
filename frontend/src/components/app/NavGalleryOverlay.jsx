import { AnimatePresence, motion } from 'framer-motion'

export default function NavGalleryOverlay({ isOpen, navItems, t, onClose, onNavigate }) {
  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          className="nav-gallery-overlay"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
        >
          <p className="nav-gallery-title">{t('nav_gallery_title')}</p>
          <motion.div
            className="nav-gallery-grid"
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ delay: 0.1 }}
            onClick={(e) => e.stopPropagation()}
          >
            {navItems.map((item, idx) => (
              <motion.button
                key={item.key}
                className="nav-gallery-item"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.05 * idx }}
                whileHover={{ scale: 1.04 }}
                whileTap={{ scale: 0.97 }}
                onClick={() => onNavigate(item.path)}
              >
                {item.icon}
                <span>{t(item.key)}</span>
              </motion.button>
            ))}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
