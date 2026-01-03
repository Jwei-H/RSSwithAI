// 通用类型定义

// 分页响应
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
  pageable: {
    pageNumber: number
    pageSize: number
    offset: number
    paged: boolean
    unpaged: boolean
  }
}

// RSS 源类型
export enum SourceType {
  ORIGIN = 'ORIGIN',
  RSSHUB = 'RSSHUB'
}

// RSS 源状态
export enum SourceStatus {
  ENABLED = 'ENABLED',
  DISABLED = 'DISABLED'
}

// 抓取状态
export enum FetchStatus {
  NEVER = 'NEVER',
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED',
  FETCHING = 'FETCHING'
}

// 实验状态
export enum ExperimentStatus {
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED'
}

// 分析状态
export enum AnalysisStatus {
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED'
}

// RSS 源
export interface RssSource {
  id: number
  name: string
  url: string
  type: SourceType
  description?: string
  fetchIntervalMinutes: number
  status: SourceStatus
  lastFetchStatus: FetchStatus
  lastFetchTime?: string
  lastFetchError?: string
  failureCount: number
  createdAt: string
  updatedAt: string
}

// RSS 源统计
export interface RssSourceStats {
  total: number
  statusCounts: {
    SUCCESS: number
    FAILED: number
    NEVER: number
    FETCHING: number
  }
}

// 文章
export interface Article {
  id: number
  sourceId: number
  sourceName: string
  title: string
  link: string
  guid: string
  description?: string
  rawContent?: string
  content?: string
  author?: string
  pubDate: string
  categories?: string
  fetchedAt: string
  createdAt: string
}

// 文章统计
export interface ArticleStats {
  total: number
  dailyCounts: {
    date: string
    count: number
  }[]
}

// 文章增强信息
export interface ArticleExtra {
  id: number
  articleId: number
  status: AnalysisStatus
  overview?: string
  keyInformation?: string[]
  tags?: string[]
  errorMessage?: string
  createdAt: string
}

// 模型配置
export interface ModelConfig {
  id: number
  name: string
  description?: string
  modelId: string
  temperature?: number
  topP?: number
  topK?: number
  maxTokens?: number
  seed?: number
  createdAt: string
}

// 提示词模板
export interface PromptTemplate {
  id: number
  name: string
  description?: string
  latestVersion: number
  latestVersionDetail?: PromptVersion
  createdAt: string
}

// 提示词版本
export interface PromptVersion {
  id: number
  templateId: number
  version: number
  content: string
  immutable: boolean
  createdAt: string
}

// 实验
export interface Experiment {
  id: number
  name: string
  description?: string
  articleIds: number[]
  modelConfigId: number
  modelConfigName?: string
  promptTemplateId: number
  promptTemplateName?: string
  promptVersionNum: number
  status: ExperimentStatus
  createdAt: string
}

// 分析结果
export interface AnalysisResult {
  id: number
  experimentId: number
  experimentName?: string
  articleId: number
  articleTitle?: string
  modelConfigJson: string
  promptContent: string
  analysisResult?: string
  status: AnalysisStatus
  inputTokens?: number
  outputTokens?: number
  executionTimeMs?: number
  errorMessage?: string
  createdAt: string
}

// 系统设置
export interface SystemSettings {
  [key: string]: string
}

// 登录请求
export interface LoginRequest {
  username: string
  password: string
}

// 登录响应
export interface LoginResponse {
  token: string
}

// 创建/更新 RSS 源请求
export interface RssSourceRequest {
  name: string
  url: string
  type: SourceType
  description?: string
  fetchIntervalMinutes?: number
  status?: SourceStatus
}

// 创建/更新模型配置请求
export interface ModelConfigRequest {
  name: string
  description?: string
  modelId: string
  temperature?: number
  topP?: number
  topK?: number
  maxTokens?: number
  seed?: number
}

// 创建提示词模板请求
export interface PromptTemplateRequest {
  name: string
  description?: string
}

// 更新提示词版本请求
export interface PromptVersionRequest {
  content: string
}

// 创建实验请求
export interface ExperimentRequest {
  name: string
  description?: string
  articleIds: number[]
  modelConfigId: number
  promptTemplateId: number
  promptVersionNum: number
}
