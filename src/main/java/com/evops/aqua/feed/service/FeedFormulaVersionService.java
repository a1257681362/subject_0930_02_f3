package com.evops.aqua.feed.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.feed.dto.FormulaVersionPublishRequest;
import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.entity.FeedFormulaItem;
import com.evops.aqua.feed.entity.FeedFormulaVersion;
import com.evops.aqua.feed.mapper.FeedFormulaItemMapper;
import com.evops.aqua.feed.mapper.FeedFormulaVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 投喂配方版本：配方行按 苗种 + 日龄区间 + 水温区间 匹配投喂量。
 * 发布新版本时旧版本置 SUPERSEDED 但永不删改；已锁定投喂单冻结旧版本快照，
 * 未锁定草稿重绑新版本；权限收紧期间不允许发布新版本，已确认版本事实保留。
 */
@Service
public class FeedFormulaVersionService {

    private final FeedFormulaVersionMapper versionMapper;
    private final FeedFormulaItemMapper itemMapper;
    private final FeedGovernanceService governanceService;
    private final FeedEvidenceService evidenceService;

    public FeedFormulaVersionService(FeedFormulaVersionMapper versionMapper,
                                     FeedFormulaItemMapper itemMapper,
                                     FeedGovernanceService governanceService,
                                     FeedEvidenceService evidenceService) {
        this.versionMapper = versionMapper;
        this.itemMapper = itemMapper;
        this.governanceService = governanceService;
        this.evidenceService = evidenceService;
    }

    /**
     * 发布新版本：写入版本与配方行快照，旧 ACTIVE 版本置 SUPERSEDED。
     * 未锁定草稿投喂单的重绑由 draftRebinder 回调在同一事务内完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedFormulaVersion publish(FormulaVersionPublishRequest request,
                                      Consumer<Long> draftRebinder) {
        governanceService.requireOpen("发布投喂配方版本",
                FeedChangeEvidence.ENTITY_FORMULA_VERSION, null, request.getVersionNo());

        Long duplicated = versionMapper.selectCount(
                new QueryWrapper<FeedFormulaVersion>().eq("version_no", request.getVersionNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("配方版本号已存在：" + request.getVersionNo());
        }
        validateItems(request.getItems(), request.getVersionNo());

        FeedFormulaVersion version = new FeedFormulaVersion();
        version.setVersionNo(request.getVersionNo());
        version.setStatus(FeedFormulaVersion.STATUS_ACTIVE);
        version.setRemark(request.getRemark());
        version.setPublishedTime(LocalDateTime.now());
        versionMapper.insert(version);

        for (FormulaVersionPublishRequest.FormulaItemDto dto : request.getItems()) {
            FeedFormulaItem item = new FeedFormulaItem();
            item.setVersionId(version.getId());
            item.setSpecies(dto.getSpecies());
            item.setDayAgeFrom(dto.getDayAgeFrom());
            item.setDayAgeTo(dto.getDayAgeTo());
            item.setWaterTempFromC(dto.getWaterTempFromC());
            item.setWaterTempToC(dto.getWaterTempToC());
            item.setFeedName(dto.getFeedName());
            item.setDoseKg(dto.getDoseKg());
            item.setMealCount(dto.getMealCount() == null ? 1 : dto.getMealCount());
            itemMapper.insert(item);
        }

        List<FeedFormulaVersion> actives = versionMapper.selectList(
                new QueryWrapper<FeedFormulaVersion>()
                        .eq("status", FeedFormulaVersion.STATUS_ACTIVE)
                        .ne("id", version.getId()));
        for (FeedFormulaVersion old : actives) {
            FeedFormulaVersion before = copyOf(old);
            old.setStatus(FeedFormulaVersion.STATUS_SUPERSEDED);
            versionMapper.updateById(old);
            evidenceService.record(FeedChangeEvidence.ENTITY_FORMULA_VERSION, old.getId(),
                    FeedChangeEvidence.ACTION_UPDATE, null, before, old,
                    "发布新版本" + version.getVersionNo() + "，旧配方版本停用但事实保留");
        }

        evidenceService.record(FeedChangeEvidence.ENTITY_FORMULA_VERSION, version.getId(),
                FeedChangeEvidence.ACTION_CREATE, null, null, version,
                "发布配方版本" + version.getVersionNo() + "，配方行" + request.getItems().size() + "条");

        if (draftRebinder != null) {
            draftRebinder.accept(version.getId());
        }
        return version;
    }

    /**
     * 当前生效配方版本。
     */
    public FeedFormulaVersion currentActive() {
        List<FeedFormulaVersion> actives = versionMapper.selectList(
                new QueryWrapper<FeedFormulaVersion>()
                        .eq("status", FeedFormulaVersion.STATUS_ACTIVE)
                        .orderByDesc("id").last("LIMIT 1"));
        if (actives.isEmpty()) {
            throw new IllegalArgumentException("尚无生效的投喂配方版本，请先发布");
        }
        return actives.get(0);
    }

    public FeedFormulaVersion getOrThrow(Long id) {
        FeedFormulaVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new IllegalArgumentException("配方版本不存在，id=" + id);
        }
        return version;
    }

    public List<FeedFormulaItem> listItems(Long versionId) {
        return itemMapper.selectList(new QueryWrapper<FeedFormulaItem>()
                .eq("version_id", versionId)
                .orderByAsc("species", "day_age_from", "water_temp_from_c"));
    }

    /**
     * 按 苗种 + 日龄（天）+ 水温（℃）匹配唯一配方行；区间均为闭区间。
     */
    public Optional<FeedFormulaItem> match(Long versionId, String species, int dayAge, BigDecimal waterTempC) {
        List<FeedFormulaItem> rows = itemMapper.selectList(new QueryWrapper<FeedFormulaItem>()
                .eq("version_id", versionId)
                .eq("species", species)
                .le("day_age_from", dayAge)
                .ge("day_age_to", dayAge)
                .le("water_temp_from_c", waterTempC)
                .ge("water_temp_to_c", waterTempC));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() > 1) {
            throw new IllegalArgumentException(
                    "配方版本id=" + versionId + "对苗种" + species + "日龄" + dayAge
                            + "水温" + waterTempC + "命中多条配方，区间配置存在重叠");
        }
        return Optional.of(rows.get(0));
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    private void validateItems(List<FormulaVersionPublishRequest.FormulaItemDto> items, String versionNo) {
        for (FormulaVersionPublishRequest.FormulaItemDto item : items) {
            if (item.getDayAgeFrom() < 0 || item.getDayAgeFrom() > item.getDayAgeTo()) {
                throw new IllegalArgumentException(
                        "版本" + versionNo + "苗种" + item.getSpecies() + "日龄区间不合法："
                                + item.getDayAgeFrom() + "~" + item.getDayAgeTo());
            }
            if (item.getWaterTempFromC().compareTo(item.getWaterTempToC()) > 0) {
                throw new IllegalArgumentException(
                        "版本" + versionNo + "苗种" + item.getSpecies() + "水温区间不合法："
                                + item.getWaterTempFromC() + "~" + item.getWaterTempToC());
            }
            if (item.getMealCount() != null && item.getMealCount() < 1) {
                throw new IllegalArgumentException(
                        "版本" + versionNo + "苗种" + item.getSpecies() + "餐次必须大于0");
            }
        }
        // 同苗种下：日龄区间与水温区间同时相交即配置重叠（闭区间端点相接也算重叠）
        List<FormulaVersionPublishRequest.FormulaItemDto> bySpecies = new ArrayList<>(items);
        for (int i = 0; i < bySpecies.size(); i++) {
            for (int j = i + 1; j < bySpecies.size(); j++) {
                FormulaVersionPublishRequest.FormulaItemDto a = bySpecies.get(i);
                FormulaVersionPublishRequest.FormulaItemDto b = bySpecies.get(j);
                if (!a.getSpecies().equals(b.getSpecies())) {
                    continue;
                }
                boolean dayOverlap = a.getDayAgeFrom() <= b.getDayAgeTo()
                        && b.getDayAgeFrom() <= a.getDayAgeTo();
                boolean tempOverlap = a.getWaterTempFromC().compareTo(b.getWaterTempToC()) <= 0
                        && b.getWaterTempFromC().compareTo(a.getWaterTempToC()) <= 0;
                if (dayOverlap && tempOverlap) {
                    throw new IllegalArgumentException(
                            "版本" + versionNo + "苗种" + a.getSpecies()
                                    + "配方区间重叠：日龄" + a.getDayAgeFrom() + "~" + a.getDayAgeTo()
                                    + "/水温" + a.getWaterTempFromC() + "~" + a.getWaterTempToC()
                                    + " 与 日龄" + b.getDayAgeFrom() + "~" + b.getDayAgeTo()
                                    + "/水温" + b.getWaterTempFromC() + "~" + b.getWaterTempToC());
                }
            }
        }
    }

    private FeedFormulaVersion copyOf(FeedFormulaVersion source) {
        FeedFormulaVersion copy = new FeedFormulaVersion();
        copy.setId(source.getId());
        copy.setVersionNo(source.getVersionNo());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        copy.setPublishedTime(source.getPublishedTime());
        return copy;
    }
}
