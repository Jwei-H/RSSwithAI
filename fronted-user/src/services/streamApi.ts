import { ApiError } from './api'
import { getToken } from '../stores/session'

const BASE_URL = import.meta.
    env.VITE_API_BASE_URL ?? ''

export type TopicEventTrackingStreamHandlers = {
  onMeta?: (data: { topic?: string; articleCount?: number }) => void
  onChunk?: (data: { text?: string }) => void
  onDone?: (data: { saved: boolean; message?: string }) => void
  onError?: (data: { message?: string }) => void
}

type SseEvent = {
  event: string
  data: string
}

export async function streamTopicEventTracking(
  scope: 'subscription' | 'topic',
  id: number,
  handlers: TopicEventTrackingStreamHandlers,
  signal?: AbortSignal
) {
  const token = getToken()
  const path = scope === 'topic'
    ? `/api/front/v1/topics/${id}/event-tracking/generate`
    : `/api/front/v1/subscriptions/${id}/event-tracking/generate`
  const response = await fetch(
    `${BASE_URL}${path}`,
    {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      signal
    }
  )

  if (!response.ok) {
    throw new ApiError(response.statusText || '请求失败', response.status)
  }
  if (!response.body) {
    throw new ApiError('浏览器不支持流式响应', 0)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const events = drainEvents(buffer)
      buffer = events.rest
      for (const event of events.items) {
        dispatchEvent(event, handlers)
      }
    }

    buffer += decoder.decode()
    const events = drainEvents(buffer + '\n\n')
    for (const event of events.items) {
      dispatchEvent(event, handlers)
    }
  } finally {
    reader.releaseLock()
  }
}

function drainEvents(buffer: string): { items: SseEvent[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const parts = normalized.split('\n\n')
  const rest = parts.pop() ?? ''
  return {
    items: parts.map(parseEvent).filter((event): event is SseEvent => event !== null),
    rest
  }
}

function parseEvent(block: string): SseEvent | null {
  let event = 'message'
  const dataLines: string[] = []

  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (!dataLines.length) return null
  return { event, data: dataLines.join('\n') }
}

function dispatchEvent(event: SseEvent, handlers: TopicEventTrackingStreamHandlers) {
  const payload = safeJson(event.data)
  if (event.event === 'meta') {
    handlers.onMeta?.(payload as { topic?: string; articleCount?: number })
  } else if (event.event === 'chunk') {
    handlers.onChunk?.(payload as { text?: string })
  } else if (event.event === 'done') {
    handlers.onDone?.(payload as { saved: boolean; message?: string })
  } else if (event.event === 'error') {
    handlers.onError?.(payload as { message?: string })
  }
}

function safeJson(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return { text: raw }
  }
}
