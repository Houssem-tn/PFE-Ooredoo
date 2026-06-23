package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.DpmSourceBalance;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DpmSourceBalanceRepository extends MongoRepository<DpmSourceBalance, String> {

    boolean existsByDpmId(String dpmId);

    List<DpmSourceBalance> findByDpmId(String dpmId);

    List<DpmSourceBalance> findBySttId(String sttId);

    /** FIFO-eligible sources for an STT pool: oldest confirmed DPM first, only with stock left. */
    List<DpmSourceBalance> findBySttIdAndEquipmentIdAndRemainingQuantityGreaterThanOrderByConfirmedAtAsc(
            String sttId, String equipmentId, int threshold);
}
