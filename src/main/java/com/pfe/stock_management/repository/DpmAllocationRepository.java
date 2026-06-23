package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.DpmAllocation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface DpmAllocationRepository extends MongoRepository<DpmAllocation, String> {

    List<DpmAllocation> findByDpmId(String dpmId);

    List<DpmAllocation> findByDpmIdOrderByAllocatedAtAsc(String dpmId);

    List<DpmAllocation> findByDistributionId(String distributionId);

    List<DpmAllocation> findByDistributionIdIn(Collection<String> distributionIds);

    List<DpmAllocation> findByTechnicianIdIn(Collection<String> technicianIds);
}
