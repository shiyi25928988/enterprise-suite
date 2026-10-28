package io.github.shiyi.auth.user.service;

import io.github.shiyi.auth.user.dto.UserDTO;

import java.util.List;
import java.util.Optional;

/**
 * User domain service. Persistence wiring (MyBatis-Plus) lands in M3;
 * current implementation is an in-memory stub so the skeleton boots without a database.
 */
public interface UserService {

    Optional<UserDTO> findById(Long id);

    List<UserDTO> findAll();
}
