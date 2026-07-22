package com.AI_Assistant.userCenter.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequest {

    @NotNull(message = "状态值不能为空")
    @Min(value = 0, message = "状态值必须为0（禁用）或1（启用）")
    @Max(value = 1, message = "状态值必须为0（禁用）或1（启用）")
    private Integer status;
}