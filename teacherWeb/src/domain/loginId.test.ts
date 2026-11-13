import { expect, test } from 'vitest'
import { isValidLoginId, loginEmail, normalizeLoginId } from './loginId'

test('normalize trims and lower-cases', () => expect(normalizeLoginId(' Kim_01 ')).toBe('kim_01'))
test('valid ids', () => { expect(isValidLoginId('abcd')).toBe(true); expect(isValidLoginId('a_b_1234')).toBe(true) })
test('invalid ids', () => {
  for (const id of ['abc', 'a'.repeat(21), 'kim-01', '김철수']) expect(isValidLoginId(id)).toBe(false)
})
test('email', () => expect(loginEmail('kim_01')).toBe('kim_01@id.meetstudy.app'))
