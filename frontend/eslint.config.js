import js from '@eslint/js'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'
import globals from 'globals'

// Le projet n'avait aucun linter : une variable non definie ou un hook appele
// conditionnellement ne se voyait qu'a l'execution, dans le navigateur.
export default [
  { ignores: ['dist/**', 'node_modules/**', 'scratch/**', 'coverage/**'] },
  js.configs.recommended,
  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser, ...globals.es2021 },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: 'detect' } },
    plugins: { react, 'react-hooks': reactHooks },
    rules: {
      ...react.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      // Le projet n'utilise pas le nouveau transform JSX partout ; React est importe
      // explicitement la ou il le faut. Ces deux regles produiraient un bruit sans defaut.
      'react/react-in-jsx-scope': 'off',
      'react/prop-types': 'off',
      // Desactivee pour une interface en francais : l'apostrophe est une lettre courante
      // (« l'avocat », « aujourd'hui »). La regle vise l'ambiguite d'un guillemet isole en
      // anglais ; ici elle produisait 13 erreurs sur du texte parfaitement correct, ce qui
      // aurait noye la SEULE erreur reelle du depot (un setter appele sans exister).
      'react/no-unescaped-entities': 'off',
      // Une variable inutilisee est souvent le reste d'un remaniement ; les arguments
      // conserves pour la signature sont toleres via le prefixe _.
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      // Les avertissements de developpement sont acceptes, pas les appels oublies.
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
  },
  {
    files: ['**/*.{test,spec}.{js,jsx}'],
    languageOptions: { globals: { ...globals.vitest } },
  },
]
