import { describe, expect, it } from 'vitest'
import { categoryActionErrorMessage, categoryFormErrorMessage } from '~/utils/categoryError'
import type { ApiProblem } from '~/utils/problem'

function problem(overrides: Partial<ApiProblem> = {}): ApiProblem {
  return {
    status: undefined,
    detail: undefined,
    code: undefined,
    fieldErrors: {},
    ...overrides,
  }
}

describe('categoryFormErrorMessage', () => {
  it('says nothing for field errors: the form shows them next to the inputs', () => {
    expect(categoryFormErrorMessage(problem({ status: 400 }))).toBeUndefined()
  })

  it('explains a rate limit', () => {
    expect(categoryFormErrorMessage(problem({ status: 429, retryAfterSeconds: 60 }))).toContain(
      'wait',
    )
  })

  it('explains a stale session on 403', () => {
    expect(categoryFormErrorMessage(problem({ status: 403 }))).toBe(
      'Your session expired. Reload the page and try again.',
    )
  })

  it('explains a missing category on 404', () => {
    expect(categoryFormErrorMessage(problem({ status: 404 }))).toBe(
      'This category no longer exists.',
    )
  })

  it('falls back to a generic message otherwise', () => {
    expect(categoryFormErrorMessage(problem({ status: 500 }))).toBe(
      'Something went wrong. Please try again.',
    )
  })
})

describe('categoryActionErrorMessage', () => {
  it('shows the field error text directly: there is no field to put it next to', () => {
    expect(
      categoryActionErrorMessage(
        problem({ status: 400, fieldErrors: { orderedIds: ['must list every category'] } }),
      ),
    ).toBe('must list every category')
  })

  it('explains a rate limit', () => {
    expect(categoryActionErrorMessage(problem({ status: 429, retryAfterSeconds: 60 }))).toContain(
      'wait',
    )
  })

  it('falls back to a generic message otherwise', () => {
    expect(categoryActionErrorMessage(problem({ status: 500 }))).toBe(
      'Something went wrong. Please try again.',
    )
  })
})
