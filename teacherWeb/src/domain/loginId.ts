// Same rules as shared/.../data/LoginIds.kt.
const ID = /^[a-z0-9_]{4,20}$/

export const normalizeLoginId = (raw: string) => raw.trim().toLowerCase()
export const isValidLoginId = (id: string) => ID.test(id)
/** Firebase Auth needs an email; users only ever see their id. */
export const loginEmail = (id: string) => `${id}@id.meetstudy.app`
