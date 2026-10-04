package com.evops.aqua.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 养殖池档案：水深、盐度、所属海区。
 */
@TableName("t_pond")
public class Pond extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private String pondCode;
    private String pondName;
    private String seaArea;
    private BigDecimal waterDepthM;
    private BigDecimal salinityPpt;
    private String status;
    private String remark;

    public String getPondCode() { return pondCode; }
    public void setPondCode(String pondCode) { this.pondCode = pondCode; }
    public String getPondName() { return pondName; }
    public void setPondName(String pondName) { this.pondName = pondName; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public BigDecimal getWaterDepthM() { return waterDepthM; }
    public void setWaterDepthM(BigDecimal waterDepthM) { this.waterDepthM = waterDepthM; }
    public BigDecimal getSalinityPpt() { return salinityPpt; }
    public void setSalinityPpt(BigDecimal salinityPpt) { this.salinityPpt = salinityPpt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
