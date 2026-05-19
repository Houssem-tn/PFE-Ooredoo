package com.pfe.stock_management.repository;

import com.pfe.stock_management.entity.Equipment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EquipmentRepository extends MongoRepository<Equipment, String> {

    List<Equipment> findByActiveTrue();

    boolean existsByNameIgnoreCase(String name);

}
