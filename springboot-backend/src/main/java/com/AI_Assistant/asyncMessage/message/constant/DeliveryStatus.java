package com.AI_Assistant.asyncMessage.message.constant;

/**
 * [已修复 - 问题64] 消息投递状态常量类
 * 统一管理所有投递状态值，避免硬编码
 */
public final class DeliveryStatus {

    private DeliveryStatus() {
        // 私有构造器，防止实例化
    }

    /**
     * 待投递
     */
    public static final String PENDING = "pending";

    /**
     * 已投递
     */
    public static final String DELIVERED = "delivered";

    /**
     * 已确认（用户已阅读）
     */
    public static final String CONFIRMED = "confirmed";

    /**
     * 失败
     */
    public static final String FAILED = "failed";

    /**
     * 重试中
     */
    public static final String RETRYING = "retrying";

    /**
     * 判断是否为终态（不可再转换的状态）
     */
    public static boolean isTerminal(String status) {
        return DELIVERED.equals(status) || CONFIRMED.equals(status) || FAILED.equals(status);
    }

    /**
     * 判断状态转换是否合法
     * @param currentStatus 当前状态
     * @param targetStatus 目标状态
     * @return 是否允许转换
     */
    public static boolean isTransitionAllowed(String currentStatus, String targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        
        switch (currentStatus) {
            case PENDING:
                return DELIVERED.equals(targetStatus) || RETRYING.equals(targetStatus) || FAILED.equals(targetStatus);
            case RETRYING:
                return DELIVERED.equals(targetStatus) || FAILED.equals(targetStatus);
            case DELIVERED:
                return CONFIRMED.equals(targetStatus);
            case CONFIRMED:
            case FAILED:
                return false;
            default:
                return true;
        }
    }

    /**
     * 验证状态值是否合法
     */
    public static boolean isValid(String status) {
        return PENDING.equals(status) || DELIVERED.equals(status) || 
               CONFIRMED.equals(status) || FAILED.equals(status) || RETRYING.equals(status);
    }
}