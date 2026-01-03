// 日期格式化工具
import { format, parseISO } from 'date-fns'

export function formatDateTime(dateString: string | undefined): string {
  if (!dateString) return '-'
  try {
    return format(parseISO(dateString), 'yyyy-MM-dd HH:mm:ss')
  } catch {
    return dateString
  }
}

export function formatDate(dateString: string | undefined): string {
  if (!dateString) return '-'
  try {
    return format(parseISO(dateString), 'yyyy-MM-dd')
  } catch {
    return dateString
  }
}
