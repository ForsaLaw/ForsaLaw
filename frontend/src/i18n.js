import i18n from 'i18next';
import HttpBackend from 'i18next-http-backend';
import { initReactI18next } from 'react-i18next';

// Les traductions ne sont plus embarquees dans un gros objet inline (~63 Ko).
// Elles sont chargees a la demande depuis public/locales/{{lng}}/translation.json
// via i18next-http-backend. Editer les fichiers dans public/locales/*/translation.json.
//
// `i18nReady` (la promesse de init) est attendue dans main.jsx avant le premier rendu,
// pour eviter un flash des cles brutes le temps du chargement de la langue par defaut.
export const i18nReady = i18n
  .use(HttpBackend)
  .use(initReactI18next)
  .init({
    lng: 'fr', // Defaut pour la Tunisie
    fallbackLng: 'fr',
    supportedLngs: ['fr', 'en', 'ar'],
    ns: ['translation'],
    defaultNS: 'translation',
    backend: {
      loadPath: '/locales/{{lng}}/{{ns}}.json',
    },
    interpolation: {
      escapeValue: false,
    },
  });

export default i18n;
