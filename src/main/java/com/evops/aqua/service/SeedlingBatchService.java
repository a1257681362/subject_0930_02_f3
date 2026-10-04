package com.evops.aqua.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.dto.BatchStockRequest;
import com.evops.aqua.dto.HarvestRequest;
import com.evops.aqua.dto.TransferRequest;
import com.evops.aqua.entity.HarvestRecord;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.entity.TransferRecord;
import com.evops.aqua.mapper.HarvestRecordMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.mapper.TransferRecordMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 投苗批次（原始投苗单）。
 * 注意：不提供任何删除方法——原始投苗单不可删除。
 */
@Service
public class SeedlingBatchService {

    private final SeedlingBatchMapper batchMapper;
    private final PondService pondService;
    private final TransferRecordMapper transferRecordMapper;
    private final HarvestRecordMapper harvestRecordMapper;

    public SeedlingBatchService(SeedlingBatchMapper batchMapper,
                                PondService pondService,
                                TransferRecordMapper transferRecordMapper,
                                HarvestRecordMapper harvestRecordMapper) {
        this.batchMapper = batchMapper;
        this.pondService = pondService;
        this.transferRecordMapper = transferRecordMapper;
        this.harvestRecordMapper = harvestRecordMapper;
    }

    /**
     * 投苗：生成原始投苗单，批次编号全局唯一。
     */
    public SeedlingBatch stock(BatchStockRequest request) {
        Pond pond = pondService.getOrThrow(request.getPondId());
        pondService.requireActive(pond);
        Long duplicated = batchMapper.selectCount(
                new QueryWrapper<SeedlingBatch>().eq("batch_no", request.getBatchNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("批次编号已存在：" + request.getBatchNo());
        }
        if (request.getPlannedHarvestDate().isBefore(request.getStockDate())) {
            throw new IllegalArgumentException("计划出池日不能早于投苗日期");
        }
        SeedlingBatch batch = new SeedlingBatch();
        BeanUtils.copyProperties(request, batch);
        batch.setStatus(SeedlingBatch.STATUS_STOCKED);
        batchMapper.insert(batch);
        return batch;
    }

    /**
     * 换池：整批转移，生成转移记录并更新批次所在池。
     */
    @Transactional(rollbackFor = Exception.class)
    public TransferRecord transfer(Long batchId, TransferRequest request) {
        SeedlingBatch batch = getOrThrow(batchId);
        requireStocked(batch, "换池");
        Pond target = pondService.getOrThrow(request.getToPondId());
        pondService.requireActive(target);
        if (target.getId().equals(batch.getPondId())) {
            throw new IllegalArgumentException("目标池与当前池相同，无需换池");
        }
        TransferRecord record = new TransferRecord();
        record.setBatchId(batch.getId());
        record.setFromPondId(batch.getPondId());
        record.setToPondId(target.getId());
        record.setTransferDate(request.getTransferDate());
        record.setQuantity(batch.getQuantity());
        record.setReason(request.getReason());
        transferRecordMapper.insert(record);

        batch.setPondId(target.getId());
        batchMapper.updateById(batch);
        return record;
    }

    /**
     * 出池：生成出池记录，批次状态置为已出池，台账封存。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvestRecord harvest(Long batchId, HarvestRequest request) {
        SeedlingBatch batch = getOrThrow(batchId);
        requireStocked(batch, "出池");
        HarvestRecord record = new HarvestRecord();
        record.setBatchId(batch.getId());
        record.setPondId(batch.getPondId());
        record.setHarvestDate(request.getHarvestDate());
        record.setQuantity(request.getQuantity());
        record.setRemark(request.getRemark());
        harvestRecordMapper.insert(record);

        batch.setStatus(SeedlingBatch.STATUS_HARVESTED);
        batchMapper.updateById(batch);
        return record;
    }

    public SeedlingBatch getOrThrow(Long id) {
        SeedlingBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new IllegalArgumentException("投苗批次不存在，id=" + id);
        }
        return batch;
    }

    public List<SeedlingBatch> list(QueryWrapper<SeedlingBatch> wrapper) {
        return batchMapper.selectList(wrapper);
    }

    public void requireStocked(SeedlingBatch batch, String action) {
        if (!SeedlingBatch.STATUS_STOCKED.equals(batch.getStatus())) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "已出池，不能" + action);
        }
    }
}
