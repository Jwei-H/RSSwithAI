package com.jingwei.rsswithai.domain.model;

/**
 * 抓取状态枚举
 */
public enum FetchStatus {
    /**
     * 从未抓取
     */
    NEVER,
    
    /**
     * 抓取成功
     */
    SUCCESS,
    
    /**
     * 抓取失败
     */
    FAILED,
    
    /**
     * 正在抓取
     */
    FETCHING
}
