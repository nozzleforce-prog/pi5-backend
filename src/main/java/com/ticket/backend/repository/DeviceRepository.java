package com.ticket.backend.repository;

import com.ticket.backend.model.Device;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends MongoRepository<Device, String> {
    Optional<Device> findByDeviceId(String deviceId);
    List<Device> findByDeviceIp(String deviceIp);
    List<Device> findAllByOrderByDeviceIdAsc();
    List<Device> findByActiveTrue();
}
