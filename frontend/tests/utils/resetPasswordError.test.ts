import { describe, expect, it } from 'vitest'
import { resetPasswordError } from '~/utils/resetPasswordError'

const problem = (
  status: number | undefined,
  extra: {
    detail?: string
    fieldErrors?: Record<string, string[]>
    retryAfterSeconds?: number
  } = {},
) => ({
  status,
  detail: extra.detail,
  code: undefined,
  retryAfterSeconds: extra.retryAfterSeconds,
  fieldErrors: extra.fieldErrors ?? {},
})

describe('resetPasswordError', () => {
  it('leaves a bad password to the field errors and keeps the link', () => {
    const error = resetPasswordError(
      problem(400, { fieldErrors: { password: ['must be at least 8 characters'] } }),
    )
    expect(error).toEqual({ message: undefined, requestNewLink: false })
  })

  it('shows the backend message for a bad link and offers a new one', () => {
    const error = resetPasswordError(
      problem(400, { detail: 'This password reset link is invalid or has expired.' }),
    )
    expect(error).toEqual({
      message: 'This password reset link is invalid or has expired.',
      requestNewLink: true,
    })
  })

  it('has its own message for a bad link when the backend sent none', () => {
    expect(resetPasswordError(problem(400)).message).toContain('invalid or has expired')
  })

  it('says how long to wait when there were too many attempts, and keeps the link', () => {
    expect(resetPasswordError(problem(429, { retryAfterSeconds: 3600 }))).toEqual({
      message: 'Too many attempts. Please wait 1 hour and try again.',
      requestNewLink: false,
    })
  })

  it('tells people to reload when the CSRF token was rejected', () => {
    const error = resetPasswordError(problem(403))
    expect(error.message).toContain('Reload the page')
    expect(error.requestNewLink).toBe(false)
  })

  it('has a generic message for server and network failures', () => {
    for (const status of [500, undefined]) {
      expect(resetPasswordError(problem(status))).toEqual({
        message: 'Something went wrong. Please try again.',
        requestNewLink: false,
      })
    }
  })
})
