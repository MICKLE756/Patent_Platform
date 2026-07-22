package com.AI_Assistant.common.dto;

import java.util.List;

/**
 * 分页结果封装类
 *
 * @param <T> 数据类型
 */
public class PageResult<T> {

    /**
     * 当前页数据列表
     */
    private List<T> data;

    /**
     * 当前页码（从1开始）
     */
    private Integer pageNum;

    /**
     * 每页大小
     */
    private Integer pageSize;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 总页数
     */
    private Integer pages;

    /**
     * 默认构造函数
     */
    public PageResult() {
    }

    /**
     * 完整构造函数
     *
     * @param data     当前页数据列表
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param total    总记录数
     */
    public PageResult(List<T> data, Integer pageNum, Integer pageSize, Long total) {
        this.data = data;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.pages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
    }

    /**
     * 创建空分页结果
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(null, 1, 10, 0L);
    }

    /**
     * 创建分页结果
     *
     * @param data     当前页数据列表
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param total    总记录数
     */
    public static <T> PageResult<T> of(List<T> data, Integer pageNum, Integer pageSize, Long total) {
        return new PageResult<>(data, pageNum, pageSize, total);
    }

    // Getters and Setters

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getPages() {
        return pages;
    }

    public void setPages(Integer pages) {
        this.pages = pages;
    }

    @Override
    public String toString() {
        return "PageResult{" +
                "data=" + data +
                ", pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                ", total=" + total +
                ", pages=" + pages +
                '}';
    }
}