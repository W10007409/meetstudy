import type { Backend } from './Backend'
import { createMockBackend } from './mock'

// Task 9 adds VITE_BACKEND=firebase; until then everything runs on the keyless mock.
export const backend: Backend = createMockBackend()
