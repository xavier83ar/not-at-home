import { describe, expect, it } from 'vitest'
import { registerErrorMessage } from '~/utils/registerError'

const problem = (status: number | undefined) => ({
  status,
  detail: undefined,
  code: undefined,
  fieldErrors: {},
})

describe('registerErrorMessage', () => {
  it('leaves validation failures to the field errors', () => {
    expect(registerErrorMessage(problem(400))).toBeUndefined()
  })

  it('tells people to reload when the CSRF token was rejected', () => {
    expect(registerErrorMessage(problem(403))).toContain('Reload the page')
  })

  it('has a generic message for server and network failures', () => {
    expect(registerErrorMessage(problem(500))).toBe('Something went wrong. Please try again.')
    expect(registerErrorMessage(problem(undefined))).toBe('Something went wrong. Please try again.')
  })
})
