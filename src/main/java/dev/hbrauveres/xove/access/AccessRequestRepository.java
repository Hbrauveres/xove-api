package dev.hbrauveres.xove.access;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    List<AccessRequest> findByStatusOrderByCreatedAtAsc(AccessRequestStatus status);

    Optional<AccessRequest> findByUserIdAndStatus(Long userId, AccessRequestStatus status);
}