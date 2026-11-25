/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
  /** "true" = Demo Mode (datos ficticios en LocalStorage, sin backend). */
  readonly VITE_DEMO_MODE?: string;
}
interface ImportMeta {
  readonly env: ImportMetaEnv;
}
